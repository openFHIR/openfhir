package com.syntaric.openfhir.mapping.helpers.parser;

import com.google.gson.JsonObject;

import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.TimeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TemporalParser {

    private final FhirValueReaders fhirValueReaders;

    @Autowired
    public TemporalParser(FhirValueReaders readers) {
        this.fhirValueReaders = readers;
    }

    public DataWithIndex dateTime(JsonObject valueHolder, Integer lastIndex, String path) {
        DateTimeType dt = new DateTimeType();
        dt.setValue(fhirValueReaders.date(fhirValueReaders.get(valueHolder, path)));
        return new DataWithIndex(dt, lastIndex, path);
    }

    public DataWithIndex time(JsonObject valueHolder, Integer lastIndex, String path) {
        TimeType t = new TimeType();
        t.setValue(fhirValueReaders.get(valueHolder, path));
        return new DataWithIndex(t, lastIndex, path);
    }

    public DataWithIndex bool(JsonObject valueHolder, Integer lastIndex, String path) {
        BooleanType b = new BooleanType();
        b.setValue(Boolean.valueOf(fhirValueReaders.get(valueHolder, path)));
        return new DataWithIndex(b, lastIndex, path);
    }

    public DataWithIndex date(JsonObject valueHolder, Integer lastIndex, String path) {
        DateType d = new DateType();
        d.setValue(fhirValueReaders.date(fhirValueReaders.get(valueHolder, path)));
        return new DataWithIndex(d, lastIndex, path);
    }

    public DataWithIndex interval(List<String> joinedValues,
                                                      JsonObject valueHolder,
                                                      Integer lastIndex,
                                                      String path) {
        String lowerPath = find(joinedValues, "lower|value");
        String upperPath = find(joinedValues, "upper|value");
        Period period = new Period();
        boolean populated = false;
        if (lowerPath != null) {
            period.setStart(fhirValueReaders.date(fhirValueReaders.get(valueHolder, lowerPath)));
            populated = period.getStart() != null;
        }
        if (upperPath != null) {
            period.setEnd(fhirValueReaders.date(fhirValueReaders.get(valueHolder, upperPath)));
            populated = populated || period.getEnd() != null;
        }
        if (!populated) {
            return null;
        }
        return new DataWithIndex(period, lastIndex, path);
    }

    public DataWithIndex range(List<String> joinedValues,
                                                   JsonObject valueHolder,
                                                   Integer lastIndex,
                                                   String path) {
        Quantity low = parseIntervalQuantity(joinedValues, valueHolder, "lower");
        Quantity high = parseIntervalQuantity(joinedValues, valueHolder, "upper");

        if (low == null && high == null) {
            return null;
        }

        Range range = new Range();
        if (low != null) {
            range.setLow(low);
        }
        if (high != null) {
            range.setHigh(high);
        }
        return new DataWithIndex(range, lastIndex, path);
    }

    private Quantity parseIntervalQuantity(List<String> joinedValues, JsonObject valueHolder, String side) {
        String magnitudePath = find(joinedValues, side + "|magnitude");
        String unitPath = find(joinedValues, side + "|unit");
        String codePath = find(joinedValues, side + "|code");
        String valuePath = find(joinedValues, side + "|value");

        Quantity q = new Quantity();
        boolean populated = false;

        if (magnitudePath != null) {
            Object n = fhirValueReaders.number(fhirValueReaders.get(valueHolder, magnitudePath));
            if (n instanceof Long l) {
                q.setValue(l);
                populated = true;
            }
            if (n instanceof Double d) {
                q.setValue(d);
                populated = true;
            }
        }
        if (unitPath != null) {
            String unit = fhirValueReaders.get(valueHolder, unitPath);
            if (StringUtils.isNotBlank(unit)) {
                q.setUnit(unit);
                populated = true;
            }
        }
        if (valuePath != null) {
            String unit = fhirValueReaders.get(valueHolder, valuePath);
            if (StringUtils.isNotBlank(unit)) {
                q.setUnit(unit);
                populated = true;
            }
        }
        if (codePath != null) {
            String code = fhirValueReaders.get(valueHolder, codePath);
            if (StringUtils.isNotBlank(code)) {
                q.setCode(code);
                populated = true;
            }
        }

        return populated ? q : null;
    }

    public DataWithIndex eventInterval(List<String> joinedValues,
                                       JsonObject valueHolder,
                                       Integer lastIndex,
                                       String path) {
        String timePath = resolveEventPartPath(joinedValues, valueHolder, path, "/time");
        if (timePath == null) {
            return null;
        }
        Date start = fhirValueReaders.date(fhirValueReaders.get(valueHolder, timePath));
        if (start == null) {
            return null;
        }
        Period period = new Period();
        period.setStart(start);

        String widthPath = resolveEventPartPath(joinedValues, valueHolder, path, "/width");
        if (widthPath != null) {
            String width = fhirValueReaders.get(valueHolder, widthPath);
            if (StringUtils.isNotBlank(width)) {
                try {
                    Duration duration = Duration.parse(width);
                    if (!duration.isNegative() && !duration.isZero()) {
                        period.setEnd(Date.from(start.toInstant().plus(duration)));
                    }
                } catch (Exception ignored) {
                    // invalid duration - leave end unset
                }
            }
        }
        return new DataWithIndex(period, lastIndex, path);
    }

    public DataWithIndex eventPoint(List<String> joinedValues,
                                                        JsonObject valueHolder,
                                                        Integer lastIndex,
                                                        String path) {
        String timePath = resolveEventPartPath(joinedValues, valueHolder, path, "/time");
        if (timePath == null) {
            return null;
        }
        Date time = fhirValueReaders.date(fhirValueReaders.get(valueHolder, timePath));
        if (time == null) {
            return null;
        }
        DateTimeType dt = new DateTimeType();
        dt.setValue(time);
        return new DataWithIndex(dt, lastIndex, path);
    }

    public DataWithIndex eventByWidth(List<String> joinedValues,
                                                          JsonObject valueHolder,
                                                          Integer lastIndex,
                                                          String path) {
        String widthPath = resolveEventPartPath(joinedValues, valueHolder, path, "/width");
        boolean hasMathFunction = joinedValues != null && joinedValues.stream()
                .anyMatch(s -> s.contains("/math_function"));
        if (!hasMathFunction && valueHolder != null && path != null) {
            String basePath = path.contains("|") ? path.substring(0, path.indexOf("|")) : path;
            String eventRoot = basePath.replaceAll("/(math_function|width|time).*", "");
            String mathFunctionPrefix = eventRoot + "/math_function";
            hasMathFunction = valueHolder.keySet().stream().anyMatch(k -> k.startsWith(mathFunctionPrefix));
        }
        if (widthPath != null || hasMathFunction) {
            return eventInterval(joinedValues, valueHolder, lastIndex, path);
        }
        return eventPoint(joinedValues, valueHolder, lastIndex, path);
    }

    private String find(final List<String> joinedValues, final String suffix) {
        if (joinedValues == null) {
            return null;
        }
        return joinedValues.stream().filter(s -> s.endsWith(suffix)).findFirst().orElse(null);
    }

    private String findEventPart(final List<String> joinedValues, final String suffix) {
        if (joinedValues == null) {
            return null;
        }
        return joinedValues.stream()
                .filter(s -> s.endsWith(suffix))
                .findFirst()
                .orElse(null);
    }

    private String resolveEventPartPath(final List<String> joinedValues,
                                        final JsonObject valueHolder,
                                        final String path,
                                        final String suffix) {
        String direct = findEventPart(joinedValues, suffix);
        if (direct != null) {
            return direct;
        }
        if (valueHolder == null || path == null) {
            return null;
        }
        String basePath = path.contains("|") ? path.substring(0, path.indexOf("|")) : path;
        String eventRoot = basePath.replaceAll("/(math_function|width|time).*", "");
        String candidate = eventRoot + suffix;
        return valueHolder.has(candidate) ? candidate : null;
    }
}
