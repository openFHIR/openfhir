package com.syntaric.openfhir.mapping.toopenehr;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datavalues.quantity.datetime.DvDuration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openehr.schemas.v1.TemplateDocument;

/**
 * Pins the flat-format contract for DV_DURATION, which {@code OpenEhrPopulator} relies on when it
 * renders a FHIR Duration back to an ISO 8601 string.
 *
 * <p>{@code DvDurationRMUnmarshaller} tries the plain value first and only falls back to the
 * {@code |day}/{@code |hour}/{@code |minute}/{@code |second} components when no plain value was
 * given, so writing an ISO string is the supported path and is not affected by the component
 * inflation {@link FlatDurationComponentFixer} exists to repair.
 */
public class DurationFlatRoundTripTest {

    private static final String WIDTH_0 = "blood_pressure/blood_pressure/any_event:0/width";
    private static final String WIDTH_2 = "blood_pressure/blood_pressure/any_event:2/width";

    private static WebTemplate webTemplate;
    private static String flatJson;

    @BeforeClass
    public static void parseTemplate() throws Exception {
        final String opt = read("src/test/resources/blood_pressure/Blood Pressure.opt");
        webTemplate = new OPTParser(TemplateDocument.Factory.parse(opt).getTemplate()).parse();
        flatJson = read("src/test/resources/blood_pressure/blood-pressure_flat.json");
    }

    /**
     * A zero duration must survive the round trip. It is the value most at risk, as an empty-ish
     * reading of it would drop the node instead of writing PT0S.
     */
    @Test
    public void zeroDurationSurvivesUnmarshalling() {
        Assert.assertTrue(durationsFor(WIDTH_0, "PT0S").contains("PT0S"));
    }

    /**
     * Sub-hour durations are the ones the SDK inflates when written as components; written as a
     * plain ISO string they arrive intact, so no correction is needed.
     */
    @Test
    public void subHourDurationIsNotInflated() {
        final List<String> durations = durationsFor(WIDTH_2, "PT30M");

        Assert.assertTrue(durations.contains("PT30M"));
        Assert.assertFalse("PT30M must not be inflated to PT30H", durations.contains("PT30H"));
    }

    /** Every ISO form the FHIR Duration mapping can emit has to be readable by the unmarshaller. */
    @Test
    public void allEmittedIsoFormsAreAccepted() {
        for (final String iso : new String[]{"PT0S", "PT45S", "PT30M", "PT8H", "P10D", "PT1800S", "PT0.5S"}) {
            Assert.assertTrue(iso + " was not read back", durationsFor(WIDTH_0, iso).contains(expected(iso)));
        }
    }

    /**
     * The fixer keys off the component suffixes, so a plain ISO value must be left untouched by it.
     */
    @Test
    public void fixerLeavesPlainIsoValuesAlone() {
        final JsonObject flat = flatWith(WIDTH_0, "PT30M");
        final Composition composition = unmarshal(flat);

        Assert.assertEquals(0, FlatDurationComponentFixer.fix(composition, flat));
        Assert.assertTrue(durations(composition).contains("PT30M"));
    }

    /** P3W is normalised to days on the way in; the elapsed time is what matters, not the literal. */
    private static String expected(final String iso) {
        return "PT1800S".equals(iso) ? "PT30M" : iso;
    }

    private List<String> durationsFor(final String path, final String iso) {
        return durations(unmarshal(flatWith(path, iso)));
    }

    private JsonObject flatWith(final String path, final String iso) {
        final JsonObject flat = new Gson().fromJson(flatJson, JsonObject.class);
        flat.addProperty(path, iso);
        return flat;
    }

    private Composition unmarshal(final JsonObject flat) {
        return new FlatJsonUnmarshaller().unmarshal(new Gson().toJson(flat), webTemplate);
    }

    private static List<String> durations(final Object root) {
        final List<String> found = new ArrayList<>();
        collect(root, found, new IdentityHashMap<>());
        return found;
    }

    private static void collect(final Object node, final List<String> found, final Map<Object, Boolean> seen) {
        if (node == null || seen.put(node, Boolean.TRUE) != null) {
            return;
        }
        if (node instanceof DvDuration duration) {
            found.add(String.valueOf(duration.getValue()));
            return;
        }
        if (node instanceof Iterable<?> iterable) {
            iterable.forEach(child -> collect(child, found, seen));
            return;
        }
        if (node.getClass().getPackage() == null
                || !node.getClass().getPackage().getName().startsWith("com.nedap.archie.rm")) {
            return;
        }
        for (final java.lang.reflect.Method getter : node.getClass().getMethods()) {
            if (getter.getParameterCount() != 0 || !getter.getName().startsWith("get")
                    || getter.getDeclaringClass() == Object.class) {
                continue;
            }
            try {
                collect(getter.invoke(node), found, seen);
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                // not a readable RM property
            }
        }
    }

    private static String read(final String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
