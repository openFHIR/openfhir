package com.syntaric.openfhir.mapping.custommappings;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.mapping.helpers.parser.FhirValueReaders;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

final class TimingFlatMapper {

    private TimingFlatMapper() {}

    enum FrequencyFormat {
        DIRECT,
        QUANTITY_VALUE,
        INTERVAL_QUANTITY_VALUE
    }

    enum DurationFormat {
        DIRECT,
        DURATION_VALUE
    }

    static FrequencyFormat detectFrequencyFormat(JsonObject flat, String basePath) {
        if (flat == null || StringUtils.isBlank(basePath)) {
            return FrequencyFormat.DIRECT;
        }
        if (flat.has(basePath + "/interval<dv_quantity>_value|magnitude")
                || flat.has(basePath + "/interval<dv_quantity>_value/lower|magnitude")
                || flat.has(basePath + "/interval<dv_quantity>_value/upper|magnitude")) {
            return FrequencyFormat.INTERVAL_QUANTITY_VALUE;
        }
        if (flat.has(basePath + "/quantity_value|magnitude")
                || flat.has(basePath + "/quantity_value/lower|magnitude")
                || flat.has(basePath + "/quantity_value/upper|magnitude")) {
            return FrequencyFormat.QUANTITY_VALUE;
        }
        return FrequencyFormat.DIRECT;
    }

    static DurationFormat detectDurationFormat(JsonObject flat, String basePath) {
        if (flat == null || StringUtils.isBlank(basePath)) {
            return DurationFormat.DIRECT;
        }
        if (flat.has(basePath + "/duration_value|value")
                || flat.has(basePath + "/duration_value/lower|value")
                || flat.has(basePath + "/duration_value/upper|value")) {
            return DurationFormat.DURATION_VALUE;
        }
        return DurationFormat.DIRECT;
    }

    static void writeFrequency(JsonObject flat, String basePath, Double frequency, Double frequencyMax, String unit) {
        if (flat == null || StringUtils.isBlank(basePath) || frequency == null) {
            return;
        }
        clearFrequency(flat, basePath);
        if (frequencyMax != null) {
            String prefix = basePath + "/interval<dv_quantity>_value";
            set(flat, prefix + "/lower|magnitude", frequency);
            set(flat, prefix + "/upper|magnitude", frequencyMax);
            if (StringUtils.isNotBlank(unit)) {
                set(flat, prefix + "/lower|unit", unit);
                set(flat, prefix + "/upper|unit", unit);
            }
            return;
        }
        String prefix = basePath + "/quantity_value";
        set(flat, prefix + "|magnitude", frequency);
        if (StringUtils.isNotBlank(unit)) {
            set(flat, prefix + "|unit", unit);
        }
    }

    static void writePeriodDuration(JsonObject flat, String basePath, String duration, String durationMax) {
        if (flat == null || StringUtils.isBlank(basePath) || StringUtils.isBlank(duration)) {
            return;
        }
        DurationFormat format = detectDurationFormat(flat, basePath);
        if (StringUtils.isNotBlank(durationMax) && !durationMax.equals(duration)) {
            if (format == DurationFormat.DURATION_VALUE) {
                set(flat, basePath + "/duration_value/lower|value", duration);
                set(flat, basePath + "/duration_value/upper|value", durationMax);
            } else {
                set(flat, basePath + "/lower|value", duration);
                set(flat, basePath + "/upper|value", durationMax);
            }
            return;
        }
        if (format == DurationFormat.DURATION_VALUE) {
            set(flat, basePath + "/duration_value|value", duration);
        } else {
            set(flat, basePath + "|value", duration);
        }
    }

    static void writePeriodDurationValue(JsonObject flat, String basePath, String duration, String durationMax) {
        if (flat == null || StringUtils.isBlank(basePath) || StringUtils.isBlank(duration)) {
            return;
        }
        if (StringUtils.isNotBlank(durationMax) && !durationMax.equals(duration)) {
            set(flat, basePath + "/duration_value/lower|value", duration);
            set(flat, basePath + "/duration_value/upper|value", durationMax);
            return;
        }
        set(flat, basePath + "/duration_value|value", duration);
    }

    static void writeDailyPeriod(JsonObject flat,
                                 String basePath,
                                 Double period,
                                 Double periodMax,
                                 String unitCode) {
        if (flat == null || StringUtils.isBlank(basePath) || period == null || StringUtils.isBlank(unitCode)) {
            return;
        }
        String component = switch (unitCode) {
            case "d" -> "day";
            case "h" -> "hour";
            case "min" -> "minute";
            case "s" -> "second";
            default -> null;
        };
        if (component == null) {
            return;
        }

        // Ensure daily period does not collide with duration-value based representations.
        flat.remove(basePath);
        flat.remove(basePath + "|value");
        flat.remove(basePath + "/duration_value|value");
        flat.remove(basePath + "/duration_value/lower|value");
        flat.remove(basePath + "/duration_value/upper|value");
        flat.remove(basePath + "/lower|value");
        flat.remove(basePath + "/upper|value");

        if (periodMax != null && !periodMax.equals(period)) {
            set(flat, basePath + "/lower|" + component, normalizeDurationComponentNumber(period));
            set(flat, basePath + "/upper|" + component, normalizeDurationComponentNumber(periodMax));
            return;
        }
        set(flat, basePath + "|" + component, normalizeDurationComponentNumber(period));
    }

    static void writeNonDailyPeriod(JsonObject flat,
                                    String basePath,
                                    Double period,
                                    Double periodMax,
                                    String unitCode) {
        if (flat == null || StringUtils.isBlank(basePath) || period == null || StringUtils.isBlank(unitCode)) {
            return;
        }
        String component = switch (unitCode) {
            case "d" -> "day";
            case "wk" -> "week";
            case "mo" -> "month";
            case "a" -> "year";
            default -> null;
        };
        if (component == null) {
            return;
        }

        // Ensure non-daily period does not collide with duration-value based representations.
        flat.remove(basePath);
        flat.remove(basePath + "|value");
        flat.remove(basePath + "/duration_value|value");
        flat.remove(basePath + "/duration_value/lower|value");
        flat.remove(basePath + "/duration_value/upper|value");
        flat.remove(basePath + "/lower|value");
        flat.remove(basePath + "/upper|value");

        if (periodMax != null && !periodMax.equals(period)) {
            set(flat, basePath + "/lower|" + component, normalizeDurationComponentNumber(period));
            set(flat, basePath + "/upper|" + component, normalizeDurationComponentNumber(periodMax));
            return;
        }
        set(flat, basePath + "|" + component, normalizeDurationComponentNumber(period));
    }

    static FrequencyValue readFrequency(JsonObject valueHolder, List<String> joinedValues, FhirValueReaders readers) {

        if (valueHolder == null || joinedValues == null || readers == null) {
            return null;
        }
        String lowerMag = find(joinedValues, "interval<dv_quantity>_value/lower|magnitude");
        if (lowerMag == null) lowerMag = find(joinedValues, "quantity_value/lower|magnitude");
        if (lowerMag == null) lowerMag = find(joinedValues, "lower|magnitude");
        String upperMag = find(joinedValues, "interval<dv_quantity>_value/upper|magnitude");
        if (upperMag == null) upperMag = find(joinedValues, "quantity_value/upper|magnitude");
        if (upperMag == null) upperMag = find(joinedValues, "upper|magnitude");
        String mag = find(joinedValues, "quantity_value|magnitude");
        if (mag == null) mag = find(joinedValues, "interval<dv_quantity>_value|magnitude");
        if (mag == null) mag = find(joinedValues, "magnitude");
        String unit = find(joinedValues, "quantity_value|unit");
        if (unit == null) unit = find(joinedValues, "interval<dv_quantity>_value/lower|unit");
        if (unit == null) unit = find(joinedValues, "interval<dv_quantity>_value/upper|unit");
        if (unit == null) unit = find(joinedValues, "unit");

        Double lower = null;
        Double upper = null;
        Double single = null;

        if (lowerMag != null) {
            Object n = readers.number(readers.get(valueHolder, lowerMag));
            if (n instanceof Number num) {
                lower = num.doubleValue();
            }
        }
        if (upperMag != null) {
            Object n = readers.number(readers.get(valueHolder, upperMag));
            if (n instanceof Number num) {
                upper = num.doubleValue();
            }
        }
        if (mag != null) {
            Object n = readers.number(readers.get(valueHolder, mag));
            if (n instanceof Number num) {
                single = num.doubleValue();
            }
        }
        String unitVal = unit != null ? readers.get(valueHolder, unit) : null;

        if (lower == null && upper == null && single == null) {
            return null;
        }
        Double frequency = single != null ? single : (lower != null ? lower : upper);
        Double frequencyMax = upper;
        return new FrequencyValue(frequency, frequencyMax, unitVal);
    }

    static DurationValue readDuration(JsonObject valueHolder, List<String> joinedValues, FhirValueReaders readers) {
        if (valueHolder == null || joinedValues == null || readers == null) {
            return null;
        }
        DurationValue componentDuration = readDurationFromComponents(valueHolder, joinedValues, readers);
        if (componentDuration != null) {
            return componentDuration;
        }

        String lowerVal = find(joinedValues, "lower|value");
        String upperVal = find(joinedValues, "upper|value");
        String value = find(joinedValues, "value");

        String lower = lowerVal != null ? readers.get(valueHolder, lowerVal) : null;
        String upper = upperVal != null ? readers.get(valueHolder, upperVal) : null;
        String single = value != null ? readers.get(valueHolder, value) : null;

        if (StringUtils.isBlank(lower) && StringUtils.isBlank(upper) && StringUtils.isBlank(single)) {
            return null;
        }
        String start = StringUtils.isNotBlank(single) ? single : lower;
        return new DurationValue(start, upper);
    }

    private static DurationValue readDurationFromComponents(JsonObject valueHolder,
                                                            List<String> joinedValues,
                                                            FhirValueReaders readers) {
        DurationValue val = readDurationFromComponent(valueHolder, joinedValues, readers, "day", "P", "D");
        if (val != null) return val;
        val = readDurationFromComponent(valueHolder, joinedValues, readers, "week", "P", "W");
        if (val != null) return val;
        val = readDurationFromComponent(valueHolder, joinedValues, readers, "month", "P", "M");
        if (val != null) return val;
        val = readDurationFromComponent(valueHolder, joinedValues, readers, "year", "P", "Y");
        if (val != null) return val;
        val = readDurationFromComponent(valueHolder, joinedValues, readers, "hour", "PT", "H");
        if (val != null) return val;
        val = readDurationFromComponent(valueHolder, joinedValues, readers, "minute", "PT", "M");
        if (val != null) return val;
        return readDurationFromComponent(valueHolder, joinedValues, readers, "second", "PT", "S");
    }

    private static DurationValue readDurationFromComponent(JsonObject valueHolder,
                                                           List<String> joinedValues,
                                                           FhirValueReaders readers,
                                                           String component,
                                                           String isoPrefix,
                                                           String isoSuffix) {
        String lowerPath = find(joinedValues, "lower|" + component);
        String upperPath = find(joinedValues, "upper|" + component);
        String singlePath = find(joinedValues, component);

        String lower = lowerPath != null ? toNumberString(readers.number(readers.get(valueHolder, lowerPath))) : null;
        String upper = upperPath != null ? toNumberString(readers.number(readers.get(valueHolder, upperPath))) : null;
        String single = singlePath != null ? toNumberString(readers.number(readers.get(valueHolder, singlePath))) : null;

        if (StringUtils.isBlank(lower) && StringUtils.isBlank(upper) && StringUtils.isBlank(single)) {
            return null;
        }
        String start = StringUtils.isNotBlank(single) ? single : lower;
        String startIso = StringUtils.isNotBlank(start) ? isoPrefix + start + isoSuffix : null;
        String upperIso = StringUtils.isNotBlank(upper) ? isoPrefix + upper + isoSuffix : null;
        if (StringUtils.isBlank(startIso)) {
            return null;
        }
        return new DurationValue(startIso, upperIso);
    }

    private static String toNumberString(Object value) {
        if (!(value instanceof Number n)) {
            return null;
        }
        double d = n.doubleValue();
        if (d == Math.rint(d)) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }

    private static Number normalizeDurationComponentNumber(Double value) {
        if (value == null) {
            return null;
        }
        if (value == Math.rint(value)) {
            return Long.valueOf(value.longValue());
        }
        return value;
    }

    private static String find(List<String> joinedValues, String suffix) {
        return joinedValues.stream().filter(s -> s.endsWith(suffix)).findFirst().orElse(null);
    }

    private static void set(JsonObject flat, String path, Object value) {
        if (flat == null || StringUtils.isBlank(path) || value == null) {
            return;
        }
        if (value instanceof Number n) {
            flat.addProperty(path, n);
        } else {
            flat.addProperty(path, value.toString());
        }
    }

    private static void clearFrequency(JsonObject flat, String basePath) {
        flat.remove(basePath + "|magnitude");
        flat.remove(basePath + "|unit");
        flat.remove(basePath + "/lower|magnitude");
        flat.remove(basePath + "/lower|unit");
        flat.remove(basePath + "/upper|magnitude");
        flat.remove(basePath + "/upper|unit");
        flat.remove(basePath + "/quantity_value|magnitude");
        flat.remove(basePath + "/quantity_value|unit");
        flat.remove(basePath + "/quantity_value/lower|magnitude");
        flat.remove(basePath + "/quantity_value/lower|unit");
        flat.remove(basePath + "/quantity_value/upper|magnitude");
        flat.remove(basePath + "/quantity_value/upper|unit");
        flat.remove(basePath + "/interval<dv_quantity>_value|magnitude");
        flat.remove(basePath + "/interval<dv_quantity>_value|unit");
        flat.remove(basePath + "/interval<dv_quantity>_value/lower|magnitude");
        flat.remove(basePath + "/interval<dv_quantity>_value/lower|unit");
        flat.remove(basePath + "/interval<dv_quantity>_value/upper|magnitude");
        flat.remove(basePath + "/interval<dv_quantity>_value/upper|unit");
    }

    static final class FrequencyValue {
        final Double frequency;
        final Double frequencyMax;
        final String unit;

        FrequencyValue(Double frequency, Double frequencyMax, String unit) {
            this.frequency = frequency;
            this.frequencyMax = frequencyMax;
            this.unit = unit;
        }
    }

    static final class DurationValue {
        final String value;
        final String valueMax;

        DurationValue(String value, String valueMax) {
            this.value = value;
            this.valueMax = valueMax;
        }
    }
}
