package com.syntaric.openfhir.mapping.toopenehr;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.datavalues.quantity.datetime.DvDuration;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.threeten.extra.PeriodDuration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Repairs {@link DvDuration} values that the ehrbase SDK's flat-json unmarshaller decodes incorrectly.
 *
 * <p>A DV_DURATION can only be written to the flat format through the {@code |day}, {@code |hour},
 * {@code |minute} and {@code |second} component suffixes; the node rejects a plain ISO-8601 string.
 * {@code DvDurationRMUnmarshaller} in ehrbase SDK 2.19.0 however builds the value as
 * {@code ofHours(hour) + ofHours(minute) + ofHours(second)}, so every sub-hour component is inflated
 * to hours: a 30 minute infusion rate is decoded as {@code PT30H} instead of {@code PT30M}.
 *
 * <p>Since the corruption is deterministic and the flat json we handed to the unmarshaller still holds
 * the intended components, the value can be recomputed exactly rather than guessed at. Only durations
 * that actually carry a sub-hour component are touched, so day/hour-only values (which the SDK decodes
 * correctly) are left untouched.
 *
 * <p>Corrections are matched by value rather than by path, as flat paths cannot be resolved back to RM
 * nodes here. A corrupted value is therefore only corrected when it is unambiguous: if the composition
 * also asks for that same duration legitimately (through a node the SDK decodes correctly), the value
 * is left alone rather than risk rewriting an unrelated duration.
 */
@Slf4j
public class FlatDurationComponentFixer {

    private static final String DAY = "|day";
    private static final String HOUR = "|hour";
    private static final String MINUTE = "|minute";
    private static final String SECOND = "|second";

    private FlatDurationComponentFixer() {
    }

    /**
     * Walks the decoded composition and corrects every DV_DURATION the unmarshaller inflated.
     *
     * @return the number of corrected values
     */
    public static int fix(final Object rmObject, final JsonObject flat) {
        final Map<Duration, Duration> corrections = corruptedToIntended(flat);
        if (corrections.isEmpty()) {
            return 0;
        }
        return walk(rmObject, corrections, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static int walk(final Object node, final Map<Duration, Duration> corrections, final Set<Object> visited) {
        if (node == null || !visited.add(node)) {
            return 0;
        }
        if (node instanceof DvDuration duration) {
            return correct(duration, corrections) ? 1 : 0;
        }
        if (node instanceof Iterable<?> iterable) {
            int corrected = 0;
            for (final Object child : iterable) {
                corrected += walk(child, corrections, visited);
            }
            return corrected;
        }
        if (!isRmObject(node)) {
            return 0;
        }
        int corrected = 0;
        for (final Method getter : node.getClass().getMethods()) {
            if (getter.getParameterCount() != 0 || !getter.getName().startsWith("get")
                    || getter.getDeclaringClass() == Object.class) {
                continue;
            }
            final Object child;
            try {
                child = getter.invoke(node);
            } catch (final ReflectiveOperationException | RuntimeException e) {
                log.trace("Skipping {} while scanning for durations", getter.getName(), e);
                continue;
            }
            corrected += walk(child, corrections, visited);
        }
        return corrected;
    }

    /** Restricts the walk to openEHR RM types so unrelated object graphs are not traversed. */
    private static boolean isRmObject(final Object node) {
        final Package nodePackage = node.getClass().getPackage();
        return nodePackage != null && nodePackage.getName().startsWith("com.nedap.archie.rm");
    }

    /**
     * Builds a lookup from the corrupted duration the SDK will decode to the duration the flat json
     * actually asked for. Only nodes carrying a minute or second component are included, as those are
     * the ones the SDK decodes incorrectly; day/hour-only nodes decode correctly and are left alone.
     */
    public static Map<Duration, Duration> corruptedToIntended(final JsonObject flat) {
        final Map<Duration, Duration> corrections = new HashMap<>();
        if (flat == null) {
            return corrections;
        }
        final Map<String, Map<String, Long>> componentsByPath = componentsByPath(flat);
        final Set<Duration> ambiguous = new HashSet<>();
        for (final Map.Entry<String, Map<String, Long>> entry : componentsByPath.entrySet()) {
            final Map<String, Long> components = entry.getValue();
            final Duration intended = intended(components);
            final Duration corrupted = asSdkWouldDecode(components);
            if (!components.containsKey(MINUTE) && !components.containsKey(SECOND)) {
                // decoded correctly by the SDK: its value must not be rewritten by another node's correction
                ambiguous.add(intended);
                continue;
            }
            if (corrupted.equals(intended)) {
                continue;
            }
            final Duration previous = corrections.put(corrupted, intended);
            if (previous != null && !previous.equals(intended)) {
                // two nodes decode to the same wrong value but want different durations
                ambiguous.add(corrupted);
            }
        }
        corrections.keySet().removeAll(ambiguous);
        return corrections;
    }

    /**
     * Overwrites a decoded duration with the value the flat json actually asked for.
     *
     * @return true if the value was corrected
     */
    public static boolean correct(final DvDuration decoded, final Map<Duration, Duration> corrections) {
        if (decoded == null || corrections == null || corrections.isEmpty()) {
            return false;
        }
        final Duration decodedDuration = asDuration(decoded.getValue());
        if (decodedDuration == null) {
            return false;
        }
        final Duration intended = corrections.get(decodedDuration);
        if (intended == null || intended.equals(decodedDuration)) {
            return false;
        }
        log.debug("Correcting DV_DURATION decoded as {} to {}", decodedDuration, intended);
        decoded.setValue(PeriodDuration.of(intended));
        return true;
    }

    /**
     * Archie models a DV_DURATION as a {@link PeriodDuration}, which keeps the date and time parts
     * separate. Only values the SDK could have produced from day/hour/minute/second components are of
     * interest here, so a period made up of whole days converts cleanly to an exact duration.
     */
    private static Duration asDuration(final TemporalAmount value) {
        if (value instanceof Duration duration) {
            return duration;
        }
        if (value instanceof PeriodDuration periodDuration) {
            final Period period = periodDuration.getPeriod();
            if (period.getYears() != 0 || period.getMonths() != 0) {
                return null;
            }
            return periodDuration.getDuration().plusDays(period.getDays());
        }
        return null;
    }

    private static Map<String, Map<String, Long>> componentsByPath(final JsonObject flat) {
        final Map<String, Map<String, Long>> componentsByPath = new HashMap<>();
        for (final String key : flat.keySet()) {
            final String suffix = componentSuffix(key);
            if (suffix == null) {
                continue;
            }
            final Long component = asLong(flat.get(key));
            if (component == null) {
                continue;
            }
            final String basePath = key.substring(0, key.length() - suffix.length());
            componentsByPath.computeIfAbsent(basePath, path -> new HashMap<>()).put(suffix, component);
        }
        return componentsByPath;
    }

    private static Duration intended(final Map<String, Long> components) {
        Duration duration = Duration.ZERO;
        for (final Map.Entry<String, Long> component : components.entrySet()) {
            duration = duration.plus(toDuration(component.getKey(), component.getValue()));
        }
        return duration;
    }

    /**
     * Mirrors {@code DvDurationRMUnmarshaller}, which runs hour, minute and second alike through
     * {@code Duration.ofHours}.
     */
    private static Duration asSdkWouldDecode(final Map<String, Long> components) {
        Duration duration = Duration.ofDays(components.getOrDefault(DAY, 0L));
        for (final String suffix : new String[]{HOUR, MINUTE, SECOND}) {
            duration = duration.plus(Duration.ofHours(components.getOrDefault(suffix, 0L)));
        }
        return duration;
    }

    private static String componentSuffix(final String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        for (final String suffix : new String[]{DAY, HOUR, MINUTE, SECOND}) {
            if (key.endsWith(suffix)) {
                return suffix;
            }
        }
        return null;
    }

    private static Duration toDuration(final String suffix, final long value) {
        return switch (suffix) {
            case DAY -> Duration.ofDays(value);
            case HOUR -> Duration.ofHours(value);
            case MINUTE -> Duration.ofMinutes(value);
            case SECOND -> Duration.ofSeconds(value);
            default -> Duration.ZERO;
        };
    }

    private static Long asLong(final JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsLong();
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
