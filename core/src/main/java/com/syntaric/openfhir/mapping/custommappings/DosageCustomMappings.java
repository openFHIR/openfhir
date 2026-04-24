package com.syntaric.openfhir.mapping.custommappings;

import static com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX_ESCAPED;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.mapping.helpers.parser.FhirValueReaders;
import com.syntaric.openfhir.util.OpenEhrPopulator;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TimeType;
import org.hl7.fhir.r4.model.Timing;

@Slf4j
public class DosageCustomMappings extends CustomMapping {
    // TODO operates currently on the Flat paths this makes the mappers only usable for the KDS mappigns, there need to be some resolving to canonicalpaths back and forth implemented
    private static final Set<String> CODES = Set.of(
            "timingToDaily",
            "timingNonDaily",
            "dosageQuantityToRange",
            "rangeToText",
            "ratio_to_dv_quantity",
            "ratio_to_dosage",
            "ratio_to_dosage_action",
            "dosageDurationToAdministrationDuration"
    );
    private static final Set<String> ALLOWED_RATE_UNITS = Set.of("l/h", "ml/h", "ml/s", "ml/min");
    private static final Pattern ISO_DAY_TIME_DURATION = Pattern.compile(
            "^P(?:(\\d+(?:\\.\\d+)?)D)?(?:T(?:(\\d+(?:\\.\\d+)?)H)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$"
    );

    @Override
    public Set<String> mappingCodes() {
        return CODES;
    }

    @Override
    public boolean applyFhirToOpenEhrMapping(final MappingHelper mappingHelper,
                                             final IBase fhirValue,
                                             final List<String> possibleRmTypes,
                                             final JsonObject flat,
                                             final OpenEhrPopulator populator,
                                             final OpenFhirMapperUtils mapperUtils,
                                             final OpenFhirStringUtils stringUtils) {
        final String openEhrPath = mappingHelper.getFullOpenEhrFlatPath();
        if (StringUtils.isBlank(openEhrPath) || fhirValue == null) {
            return false;
        }
        if (possibleRmTypes.contains(FhirConnectConst.OPENEHR_TYPE_NONE)) {
            return false;
        }

        final String mappingCode = mappingHelper.getProgrammedMapping();

        return switch (mappingCode) {
            case "dosageQuantityToRange" -> toOpenEhrDosageQuantityToRange(mappingHelper, openEhrPath, fhirValue, flat, populator);
            case "rangeToText" -> toOpenEhrRangeToText(mappingHelper, openEhrPath, fhirValue, flat, populator);
            case "ratio_to_dv_quantity" -> toOpenEhrRatioToDvQuantity(mappingHelper, openEhrPath, fhirValue, flat, populator);
            case "ratio_to_dosage" -> toOpenEhrRatioToDosage(mappingHelper, openEhrPath, fhirValue, flat, populator);
            case "ratio_to_dosage_action" -> toOpenEhrRatioToDosageAction(mappingHelper, openEhrPath, fhirValue, flat, populator);
            case "timingToDaily" -> toOpenEhrTimingDaily(mappingHelper, openEhrPath, fhirValue, flat, populator);
            case "timingNonDaily" -> toOpenEhrTimingNonDaily(mappingHelper, openEhrPath, fhirValue, flat, populator);
            case "dosageDurationToAdministrationDuration" -> toOpenEhrDurationToAdministrationDuration(mappingHelper, openEhrPath, fhirValue, flat, populator);
            default -> false;
        };
    }

    @Override
    public DataWithIndex applyOpenEhrToFhirMapping(final MappingHelper mappingHelper,
                                                   final List<String> joinedValues,
                                                   final JsonObject valueHolder,
                                                   final Integer lastIndex,
                                                   final String path,
                                                   final String resourceType,
                                                   final String fhirPath,
                                                   final OpenFhirStringUtils stringUtils,
                                                   final OpenFhirMapperUtils mapperUtils) {
        if (StringUtils.isBlank(path) || joinedValues == null || joinedValues.isEmpty()) {
            return null;
        }
        final String mappingCode = mappingHelper.getProgrammedMapping();
        return switch (mappingCode) {
            case "dosageQuantityToRange" -> toFhirDose(joinedValues, valueHolder, lastIndex, path, fhirPath, mapperUtils);
            case "rangeToText" -> toFhirRangeFromText(joinedValues, valueHolder, lastIndex, path, mapperUtils);
            case "ratio_to_dv_quantity" -> toFhirRatio(joinedValues, valueHolder, lastIndex, path, mapperUtils);
            case "ratio_to_dosage" -> toFhirRatioDosage(joinedValues, valueHolder, lastIndex, path, mapperUtils);
            case "ratio_to_dosage_action" -> toFhirRatioDosageAction(joinedValues, valueHolder, lastIndex, path, mapperUtils);
            case "timingToDaily" -> toFhirTimingDaily(joinedValues, valueHolder, lastIndex, path, mapperUtils);
            case "timingNonDaily" -> toFhirTimingNonDaily(joinedValues, valueHolder, lastIndex, path, mapperUtils);
            case "dosageDurationToAdministrationDuration" -> toFhirTimingRepeat(joinedValues, valueHolder, lastIndex, path, mapperUtils);
            default -> null;
        };
    }

    private boolean toOpenEhrDosageQuantityToRange(final MappingHelper mappingHelper,
                                                   final String openEhrPath,
                                                   final IBase fhirValue,
                                                   final JsonObject flat,
                                                   final OpenEhrPopulator populator) {
        boolean isMultipleTypes = mappingHelper.getPossibleRmTypes().size() > 1;
        if (fhirValue instanceof Range range) {
            // If we previously mapped a single quantity, clear it to avoid conflicts.
            flat.remove(openEhrPath + "|magnitude");
            flat.remove(openEhrPath + "|unit");
            flat.remove(openEhrPath + "|code");
            flat.remove(openEhrPath + "|value");
            String intervalPath = toIntervalOfQuantityPath(openEhrPath);
            populator.setOpenEhrValue(mappingHelper, intervalPath, range, FhirConnectConst.DV_INTERVAL, isMultipleTypes, flat, null, null);
            return true;
        }
        if (fhirValue instanceof Quantity quantity) {
            // If a range was already mapped, keep the range and skip the quantity.
            if (flat.has(openEhrPath + "/lower|magnitude") || flat.has(openEhrPath + "/upper|magnitude")) {
                return true;
            }
            populator.setOpenEhrValue(mappingHelper, openEhrPath, quantity, FhirConnectConst.DV_QUANTITY, isMultipleTypes, flat, null, null);
            return true;
        }
        return false;
    }

    private String toIntervalOfQuantityPath(final String path) {
        if (path == null) {
            return null;
        }
        if (path.endsWith("/interval<dv_quantity>_value")) { // interval_of_time interval_of_date_time
            return path;
        }
        if (path.endsWith("/quantity_value")) {
            String base = path.substring(0, path.length() - "/quantity_value".length());
            return base + "/interval<dv_quantity>_value";
        }
        return path;
    }

    private boolean toOpenEhrRangeToText(final MappingHelper mappingHelper,
                                         final String openEhrPath,
                                         final IBase fhirValue,
                                         final JsonObject flat,
                                         final OpenEhrPopulator populator) {
        if (!(fhirValue instanceof Range range)) {
            return false;
        }
        String serialized = serializeRange(range);
        if (StringUtils.isBlank(serialized)) {
            return false;
        }
        String textPath = toTextValuePath(openEhrPath);
        boolean isMultipleTypes = mappingHelper.getPossibleRmTypes().size() > 1;
        populator.setOpenEhrValue(mappingHelper, textPath, new StringType(serialized), FhirConnectConst.DV_TEXT, isMultipleTypes, flat, null, null);
        return true;
    }

    private boolean toOpenEhrRatioToDvQuantity(final MappingHelper mappingHelper,
                                               final String openEhrPath,
                                               final IBase fhirValue,
                                               final JsonObject flat,
                                               final OpenEhrPopulator populator) {
        if (!(fhirValue instanceof Ratio ratio)) {
            return false;
        }
        Quantity numerator = ratio.getNumerator();
        Quantity denominator = ratio.getDenominator();
        if (numerator == null || denominator == null || numerator.getValue() == null) {
            return false;
        }
        Quantity q = new Quantity();
        // Keep numerator value, merge units as "numUnit/denUnit"
        q.setValue(numerator.getValue());
        String unit = buildUnit(numerator.getUnit(), denominator.getUnit());
        if (StringUtils.isNotBlank(unit)) {
            q.setUnit(unit);
        }
        String code = buildUnit(numerator.getCode(), denominator.getCode());
        if (StringUtils.isNotBlank(code)) {
            q.setCode(code);
        }
        setUcumSystemIfPresent(q);

        boolean isMultipleTypes = mappingHelper.getPossibleRmTypes().size() > 1;
        populator.setOpenEhrValue(mappingHelper, openEhrPath, q, FhirConnectConst.DV_QUANTITY, isMultipleTypes, flat, null, null);
        return true;
    }

    private boolean toOpenEhrRatioToDosage(final MappingHelper mappingHelper,
                                           final String openEhrPath,
                                           final IBase fhirValue,
                                           final JsonObject flat,
                                           final OpenEhrPopulator populator) {
        return toOpenEhrRatioToDosageInternal(
                mappingHelper,
                openEhrPath,
                fhirValue,
                flat,
                populator,
                "verabreichungsrate/quantity_value",
                "verabreichungsdauer",
                false);
    }

    private boolean toOpenEhrRatioToDosageAction(final MappingHelper mappingHelper,
                                                 final String openEhrPath,
                                                 final IBase fhirValue,
                                                 final JsonObject flat,
                                                 final OpenEhrPopulator populator) {
        return toOpenEhrRatioToDosageInternal(
                mappingHelper,
                openEhrPath,
                fhirValue,
                flat,
                populator,
                "verabreichungsrate/quantity_value",
                "verabreichungsdauer",
                true);
    }

    private boolean toOpenEhrRatioToDosageInternal(final MappingHelper mappingHelper,
                                                   final String openEhrPath,
                                                   final IBase fhirValue,
                                                   final JsonObject flat,
                                                   final OpenEhrPopulator populator,
                                                   final String rateChildPath,
                                                   final String durationChildPath,
                                                   final boolean useDurationInputSuffixes) {
        if (!(fhirValue instanceof Ratio ratio)) {
            return false;
        }
        Quantity numerator = ratio.getNumerator();
        Quantity denominator = ratio.getDenominator();
        if (numerator == null || denominator == null || numerator.getValue() == null || denominator.getValue() == null) {
            return false;
        }
        Double denomValue = denominator.getValue().doubleValue();
        if (denomValue == 0d) {
            return false;
        }
        String duration = durationStringFromQuantity(denominator);
        if (StringUtils.isBlank(duration)) {
            return false;
        }

        double rate = numerator.getValue().doubleValue() / denomValue;
        Quantity rateQuantity = new Quantity();
        rateQuantity.setValue(rate);
        String numeratorUnit = unitOrCodePreferCode(numerator);
        String denominatorUnit = unitOrCodePreferCode(denominator);
        String combinedUnit = normalizeUcumUnit(buildUnit(numeratorUnit, denominatorUnit));
        boolean isMultipleTypes = mappingHelper.getPossibleRmTypes().size() > 1;
        if (StringUtils.isNotBlank(combinedUnit) && isAllowedRateUnit(combinedUnit)) {
            rateQuantity.setUnit(combinedUnit);
            rateQuantity.setCode(combinedUnit);
            setUcumSystemIfPresent(rateQuantity);
            String ratePath = appendFlatChild(openEhrPath, rateChildPath);
            populator.setOpenEhrValue(mappingHelper, ratePath, rateQuantity, FhirConnectConst.DV_QUANTITY, isMultipleTypes, flat, null, null);
        }

        String durationPath = appendFlatChild(openEhrPath, durationChildPath);
        if (useDurationInputSuffixes) {
            if (!writeDurationInputs(durationPath, denominator, flat)) {
                populator.setOpenEhrValue(mappingHelper, durationPath, new StringType(duration), FhirConnectConst.DV_DURATION, isMultipleTypes, flat, null, null);
            }
        } else {
            populator.setOpenEhrValue(mappingHelper, durationPath, new StringType(duration), FhirConnectConst.DV_DURATION, isMultipleTypes, flat, null, null);
        }
        return true;
    }

    private boolean writeDurationInputs(final String basePath, final Quantity denominator, final JsonObject flat) {
        if (StringUtils.isBlank(basePath) || denominator == null || denominator.getValue() == null || flat == null) {
            return false;
        }
        Timing.UnitsOfTime unit = unitFromString(unitOrCodePreferCode(denominator));
        if (unit == null) {
            return false;
        }

        Double raw = denominator.getValue().doubleValue();
        if (raw == null || raw <= 0d) {
            return false;
        }

        long totalSeconds;
        switch (unit) {
            case D -> totalSeconds = Math.round(raw * 86400d);
            case H -> totalSeconds = Math.round(raw * 3600d);
            case MIN -> totalSeconds = Math.round(raw * 60d);
            case S -> totalSeconds = Math.round(raw);
            default -> {
                return false;
            }
        }
        if (totalSeconds <= 0L) {
            return false;
        }

        long days = totalSeconds / 86400L;
        long remainder = totalSeconds % 86400L;
        long hours = remainder / 3600L;
        remainder = remainder % 3600L;
        long minutes = remainder / 60L;
        long seconds = remainder % 60L;

        String durationValuePath = basePath + "/duration_value";
        flat.remove(basePath + "|day");
        flat.remove(basePath + "|hour");
        flat.remove(basePath + "|minute");
        flat.remove(basePath + "|second");
        flat.remove(durationValuePath + "|day");
        flat.remove(durationValuePath + "|hour");
        flat.remove(durationValuePath + "|minute");
        flat.remove(durationValuePath + "|second");
        flat.remove(basePath);
        flat.remove(basePath + "|value");
        flat.remove(basePath + "/duration_value|value");

        if (days > 0L) {
            flat.addProperty(durationValuePath + "|day", days);
        }
        if (hours > 0L) {
            flat.addProperty(durationValuePath + "|hour", hours);
        }
        if (minutes > 0L) {
            flat.addProperty(durationValuePath + "|minute", minutes);
        }
        if (seconds > 0L) {
            flat.addProperty(durationValuePath + "|second", seconds);
        }
        return true;
    }

    private String appendFlatChild(final String basePath, final String child) {
        if (StringUtils.isBlank(basePath) || StringUtils.isBlank(child)) {
            return basePath;
        }
        String base = basePath;
        if (base.startsWith(FhirConnectConst.OPENEHR_ARCHETYPE_FC)) {
            base = base.substring(FhirConnectConst.OPENEHR_ARCHETYPE_FC.length());
            if (base.startsWith("/")) {
                base = base.substring(1);
            }
        }
        if (base.endsWith("/" + child) || base.contains("/" + child + "/") || base.endsWith(child)) {
            return base;
        }
        return base + "/" + child;
    }

    private boolean toOpenEhrTimingDaily(final MappingHelper mappingHelper,
                                         final String openEhrPath,
                                         final IBase fhirValue,
                                         final JsonObject flat,
                                         final OpenEhrPopulator populator) {
        TimingApplyContext ctx = buildTimingApplyContext(fhirValue, true);
        if (ctx == null) {
            return false;
        }

        applyTimingShared(mappingHelper, openEhrPath, ctx, flat, populator, true);

        // Frequency -> /frequenz (DV_QUANTITY)
        if (ctx.repeat.hasFrequency() || ctx.repeat.hasFrequencyMax()) {
            Integer freq = ctx.repeat.hasFrequency() ? ctx.repeat.getFrequency() : null;
            Integer freqMax = ctx.repeat.hasFrequencyMax() ? ctx.repeat.getFrequencyMax() : null;
            if (freq != null || freqMax != null) {
                Double frequency = freq != null ? freq.doubleValue() : (freqMax != null ? freqMax.doubleValue() : null);
                Double frequencyMax = freqMax != null ? freqMax.doubleValue() : null;
                String unit = toFrequencyUnit(ctx.periodUnit);
                TimingFlatMapper.writeFrequency(flat, openEhrPath + "/frequenz", frequency, frequencyMax, unit);
            }
        }

        return true;
    }

    private boolean toOpenEhrTimingNonDaily(final MappingHelper mappingHelper,
                                            final String openEhrPath,
                                            final IBase fhirValue,
                                            final JsonObject flat,
                                            final OpenEhrPopulator populator) {
        TimingApplyContext ctx = buildTimingApplyContext(fhirValue, false);
        if (ctx == null) {
            return false;
        }
        applyTimingShared(mappingHelper, openEhrPath, ctx, flat, populator, false);
        return true;
    }

    private static final class TimingApplyContext {
        private final Timing.TimingRepeatComponent repeat;
        private final Timing.UnitsOfTime periodUnit;
        private final Double period;
        private final Double periodMax;

        private TimingApplyContext(Timing.TimingRepeatComponent repeat,
                                   Timing.UnitsOfTime periodUnit,
                                   Double period,
                                   Double periodMax) {
            this.repeat = repeat;
            this.periodUnit = periodUnit;
            this.period = period;
            this.periodMax = periodMax;
        }
    }

    private TimingApplyContext buildTimingApplyContext(final IBase fhirValue, final boolean daily) {
        if (!(fhirValue instanceof Timing.TimingRepeatComponent repeat)) {
            return null;
        }
        Timing.UnitsOfTime periodUnit = extractUnitsOfTime(repeat.getPeriodUnit());
        if (periodUnit == null) {
            return null;
        }
        String unitCode = periodUnit.toCode();
        if (daily && !isDailyPeriodUnit(unitCode)) {
            return null;
        }
        if (!daily && !isNonDailyPeriodUnit(unitCode)) {
            return null;
        }
        Double period = toDouble(repeat.getPeriod());
        Double periodMax = toDouble(repeat.getPeriodMax());
        return new TimingApplyContext(repeat, periodUnit, period, periodMax);
    }

    private void applyTimingShared(final MappingHelper mappingHelper,
                                   final String openEhrPath,
                                   final TimingApplyContext ctx,
                                   final JsonObject flat,
                                   final OpenEhrPopulator populator,
                                   final boolean dailyPeriodFormat) {
        // Specific time (timeOfDay[0]) -> /zeitpunkt (DV_TIME)
        if (ctx.repeat.hasTimeOfDay() && !ctx.repeat.getTimeOfDay().isEmpty()) {
            TimeType time = ctx.repeat.getTimeOfDay().get(0);
            if (time != null && StringUtils.isNotBlank(time.getValueAsString())) {
                boolean isMultipleTypes = mappingHelper.getPossibleRmTypes().size() > 1;
                populator.setOpenEhrValue(mappingHelper, openEhrPath + "/zeitpunkt", new TimeType(time.getValueAsString()),
                                          FhirConnectConst.DV_TIME, isMultipleTypes, flat, null, null);
            }
        }

        // Interval/period -> /periode (DV_DURATION)
        if (ctx.repeat.hasPeriod() && ctx.repeat.hasPeriodUnit()) {
            if (dailyPeriodFormat) {
                TimingFlatMapper.writeDailyPeriod(
                        flat,
                        openEhrPath + "/periode",
                        ctx.period,
                        ctx.periodMax,
                        ctx.periodUnit.toCode());
            } else {
                TimingFlatMapper.writeNonDailyPeriod(
                        flat,
                        openEhrPath + "/periode",
                        ctx.period,
                        ctx.periodMax,
                        ctx.periodUnit.toCode());
            }
        }
    }

    private boolean toOpenEhrDurationToAdministrationDuration(final MappingHelper mappingHelper,
                                                              final String openEhrPath,
                                                              final IBase fhirValue,
                                                              final JsonObject flat,
                                                              final OpenEhrPopulator populator) {
        if (!(fhirValue instanceof Timing.TimingRepeatComponent repeat)) {
            return false;
        }
        if (!repeat.hasDuration() || !repeat.hasDurationUnit()) {
            return false;
        }
        Double duration = toDouble(repeat.getDuration());
        Double durationMax = toDouble(repeat.getDurationMax());
        Timing.UnitsOfTime durationUnit = extractUnitsOfTime(repeat.getDurationUnit());
        String durationStr = buildDurationFromPeriod(duration, durationMax, durationUnit);
        if (StringUtils.isBlank(durationStr)) {
            return false;
        }
        populator.setOpenEhrValue(mappingHelper, openEhrPath, new StringType(durationStr),
                                  FhirConnectConst.DV_DURATION, mappingHelper.getPossibleRmTypes().size() > 1, flat, null, null);
        return true;
    }

    private DataWithIndex toFhirDose(final List<String> joinedValues,
                                                         final JsonObject valueHolder,
                                                         final Integer lastIndex,
                                                         final String path,
                                                         final String fhirPath,
                                                         final OpenFhirMapperUtils mapperUtils) {
        FhirValueReaders readers = new FhirValueReaders(mapperUtils);
        boolean wantsRange = fhirPath != null && fhirPath.contains("as(Range)");

        String lowerMagPath = find(joinedValues, "lower|magnitude");
        String upperMagPath = find(joinedValues, "upper|magnitude");
        boolean hasRange = lowerMagPath != null || upperMagPath != null;

        if (wantsRange || hasRange) {
            Range range = new Range();
            Quantity low = readQuantityForSide(readers, valueHolder, joinedValues, "lower");
            Quantity high = readQuantityForSide(readers, valueHolder, joinedValues, "upper");
            if (low != null) range.setLow(low);
            if (high != null) range.setHigh(high);
            if (range.getLow() == null && range.getHigh() == null) {
                return null;
            }
            return new DataWithIndex(range, lastIndex == null ? -1 : lastIndex, path, null);
        }

        // Quantity
        Quantity q = readQuantityForSide(readers, valueHolder, joinedValues, "");
        if (q == null) {
            return null;
        }
        return new DataWithIndex(q, lastIndex == null ? -1 : lastIndex, path, null);
    }

    private DataWithIndex toFhirRatio(final List<String> joinedValues,
                                        final JsonObject valueHolder,
                                        final Integer lastIndex,
                                        final String path,
                                        final OpenFhirMapperUtils mapperUtils) {
        FhirValueReaders readers = new FhirValueReaders(mapperUtils);
        String magnitudePath = find(joinedValues, "magnitude");
        if (magnitudePath == null) {
            return null;
        }
        Object n = readers.number(readers.get(valueHolder, magnitudePath));
        if (!(n instanceof Number)) {
            return null;
        }
        String unitPath = find(joinedValues, "unit");
        String unit = unitPath != null ? readers.get(valueHolder, unitPath) : null;
        if (StringUtils.isBlank(unit) || !unit.contains("/")) {
            return null;
        }
        String[] parts = unit.split("/", 2);
        Quantity numerator = new Quantity();
        numerator.setValue(((Number) n).doubleValue());
        numerator.setUnit(parts[0]);
        numerator.setSystem("http://unitsofmeasure.org");
        Quantity denominator = new Quantity();
        denominator.setValue(1);
        denominator.setUnit(parts.length > 1 ? parts[1] : null);
        denominator.setSystem("http://unitsofmeasure.org");

        Ratio ratio = new Ratio();
        ratio.setNumerator(numerator);
        ratio.setDenominator(denominator);
        return new DataWithIndex(ratio, lastIndex == null ? -1 : lastIndex, path, null);
    }

    private DataWithIndex toFhirRangeFromText(final List<String> joinedValues,
                                                                  final JsonObject valueHolder,
                                                                  final Integer lastIndex,
                                                                  final String path,
                                                                  final OpenFhirMapperUtils mapperUtils) {
        if (!hasTextLikeRangeSource(joinedValues, path, valueHolder)) {
            return null;
        }
        FhirValueReaders readers = new FhirValueReaders(mapperUtils);
        String text = extractRangeTextValue(joinedValues, valueHolder, path, readers);
        if (StringUtils.isBlank(text)) {
            return null;
        }
        Range range = parseRangeText(text);
        if (range == null || (!range.hasLow() && !range.hasHigh())) {
            return null;
        }
        return new DataWithIndex(range, lastIndex == null ? -1 : lastIndex, path, null);
    }

    private String extractRangeTextValue(final List<String> joinedValues,
                                         final JsonObject valueHolder,
                                         final String path,
                                         final FhirValueReaders readers) {
        if (joinedValues != null) {
            for (String key : joinedValues) {
                if (!isTextLikeRangeKey(key)) {
                    continue;
                }
                if (StringUtils.isBlank(key) || valueHolder == null || !valueHolder.has(key)) {
                    continue;
                }
                String value = readers.get(valueHolder, key);
                if (StringUtils.isNotBlank(value)) {
                    return value;
                }
            }
        }
        if (valueHolder == null || path == null) {
            return null;
        }
        String textPath = toTextValuePath(path);
        if (StringUtils.isNotBlank(textPath) && valueHolder.has(textPath)) {
            String value = readers.get(valueHolder, textPath);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        if (valueHolder.has(path)) {
            String value = readers.get(valueHolder, path);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        String prefixBase = stripValueSuffix(path);
        String prefixed = prefixBase.endsWith("/") ? prefixBase : prefixBase + "/";
        for (String key : valueHolder.keySet()) {
            if (key.startsWith(prefixed)) {
                if (!isTextLikeRangeKey(key)) {
                    continue;
                }
                String value = readers.get(valueHolder, key);
                if (StringUtils.isNotBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private boolean hasTextLikeRangeSource(final List<String> joinedValues,
                                           final String path,
                                           final JsonObject valueHolder) {
        if (joinedValues != null) {
            for (String key : joinedValues) {
                if (isTextLikeRangeKey(key)) {
                    return true;
                }
            }
        }
        String textPath = toTextValuePath(path);
        if (StringUtils.isNotBlank(textPath) && valueHolder != null && valueHolder.has(textPath)) {
            return true;
        }
        return isTextLikeRangeKey(path);
    }

    private boolean isTextLikeRangeKey(final String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        if (key.endsWith("/text_value") || key.endsWith("text_value")) {
            return true;
        }
        return !key.contains("|");
    }

    private String toTextValuePath(final String path) {
        if (StringUtils.isBlank(path)) {
            return path;
        }
        if (path.endsWith("/quantity_value")) {
            return path.substring(0, path.length() - "/quantity_value".length()) + "/text_value";
        }
        if (path.endsWith("/interval<dv_quantity>_value")) {
            return path.substring(0, path.length() - "/interval<dv_quantity>_value".length()) + "/text_value";
        }
        return path;
    }

    private String stripValueSuffix(final String path) {
        if (StringUtils.isBlank(path)) {
            return path;
        }
        if (path.endsWith("/quantity_value")) {
            return path.substring(0, path.length() - "/quantity_value".length());
        }
        if (path.endsWith("/text_value")) {
            return path.substring(0, path.length() - "/text_value".length());
        }
        if (path.endsWith("/interval<dv_quantity>_value")) {
            return path.substring(0, path.length() - "/interval<dv_quantity>_value".length());
        }
        return path;
    }

    private DataWithIndex toFhirRatioDosage(final List<String> joinedValues,
                                                                final JsonObject valueHolder,
                                                                final Integer lastIndex,
                                                                final String path,
                                                                final OpenFhirMapperUtils mapperUtils) {
        return toFhirRatioDosageInternal(joinedValues, valueHolder, lastIndex, path, mapperUtils, false);
    }

    private DataWithIndex toFhirRatioDosageAction(final List<String> joinedValues,
                                                                      final JsonObject valueHolder,
                                                                      final Integer lastIndex,
                                                                      final String path,
                                                                      final OpenFhirMapperUtils mapperUtils) {
        return toFhirRatioDosageInternal(joinedValues, valueHolder, lastIndex, path, mapperUtils, true);
    }

    private DataWithIndex toFhirRatioDosageInternal(final List<String> joinedValues,
                                                                        final JsonObject valueHolder,
                                                                        final Integer lastIndex,
                                                                        final String path,
                                                                        final OpenFhirMapperUtils mapperUtils,
                                                                        final boolean actionPaths) {
        FhirValueReaders readers = new FhirValueReaders(mapperUtils);

        Quantity rateQuantity = readRateQuantity(readers, valueHolder, actionPaths);
        Quantity doseQuantity = readDoseQuantity(readers, valueHolder);

        String durationPath = findAnyInValueHolder(valueHolder,
                "verabreichungsdauer|value",
                "verabreichungsdauer/duration_value|value",
                "verabreichungsdauer/duration_value",
                "verabreichungsdauer");
        String duration = durationPath != null ? readers.get(valueHolder, durationPath) : null;
        if (StringUtils.isBlank(duration)) {
            // fallback to existing ratio parsing if duration is missing
            return toFhirRatio(joinedValues, valueHolder, lastIndex, path, mapperUtils);
        }
        DurationParts parts = parseIsoDuration(duration);
        if (parts == null || parts.value == null || parts.unit == null) {
            return toFhirRatio(joinedValues, valueHolder, lastIndex, path, mapperUtils);
        }

        Quantity numeratorSource = null;
        Double numeratorValue = null;
        if (rateQuantity != null && rateQuantity.getValue() != null) {
            numeratorSource = rateQuantity;
            numeratorValue = rateQuantity.getValue().doubleValue() * parts.value;
        } else if (doseQuantity != null && doseQuantity.getValue() != null) {
            // If rate is missing, derive the ratio directly from dose and duration.
            numeratorSource = doseQuantity;
            numeratorValue = doseQuantity.getValue().doubleValue();
        } else {
            return toFhirRatio(joinedValues, valueHolder, lastIndex, path, mapperUtils);
        }

        Quantity numerator = new Quantity();
        numerator.setValue(numeratorValue);
        numerator.setUnit(numeratorSource.getUnit());
        numerator.setCode(numeratorSource.getCode());
        numerator.setSystem("http://unitsofmeasure.org");

        Quantity denominator = new Quantity();
        String denomUnit = unitToCode(parts.unit);
        denominator.setValue(parts.value);
        denominator.setUnit(denomUnit);
        denominator.setCode(denomUnit);
        denominator.setSystem("http://unitsofmeasure.org");
        Ratio ratio = new Ratio();
        ratio.setNumerator(numerator);
        ratio.setDenominator(denominator);
        return new DataWithIndex(ratio, lastIndex == null ? -1 : lastIndex, path, null);
    }

    private Quantity readRateQuantity(final FhirValueReaders readers,
                                      final JsonObject valueHolder,
                                      final boolean actionPaths) {
        String magPath = findAnyInValueHolder(valueHolder,
                "verabreichungsrate/quantity_value|magnitude",
                "verabreichungsrate|magnitude");
        String unitPath = findAnyInValueHolder(valueHolder,
                "verabreichungsrate/quantity_value|unit",
                "verabreichungsrate|unit");
        if (magPath != null) {
            Object number = readers.number(readers.get(valueHolder, magPath));
            if (number instanceof Number num) {
                Quantity quantity = new Quantity();
                quantity.setValue(num.doubleValue());
                if (unitPath != null) {
                    String unit = readers.get(valueHolder, unitPath);
                    quantity.setUnit(unit);
                    quantity.setCode(unit);
                }
                setUcumSystemIfPresent(quantity);
                return quantity;
            }
        }
        return null;
    }

    private Quantity readDoseQuantity(final FhirValueReaders readers,
                                      final JsonObject valueHolder) {
        String magPath = findInValueHolder(valueHolder, "dosis/quantity_value|magnitude");
        if (magPath == null) magPath = findInValueHolder(valueHolder, "dosis|magnitude");
        String unitPath = findInValueHolder(valueHolder, "dosis/quantity_value|unit");
        if (unitPath == null) unitPath = findInValueHolder(valueHolder, "dosis|unit");
        String codePath = findInValueHolder(valueHolder, "dosis/quantity_value|code");
        if (codePath == null) codePath = findInValueHolder(valueHolder, "dosis|code");
        if (magPath == null) {
            return null;
        }
        Object number = readers.number(readers.get(valueHolder, magPath));
        if (!(number instanceof Number num)) {
            return null;
        }
        Quantity quantity = new Quantity();
        quantity.setValue(num.doubleValue());
        String unit = unitPath != null ? readers.get(valueHolder, unitPath) : null;
        String code = codePath != null ? readers.get(valueHolder, codePath) : null;
        if (StringUtils.isNotBlank(unit)) {
            quantity.setUnit(unit);
        }
        if (StringUtils.isNotBlank(code)) {
            quantity.setCode(code);
        } else if (StringUtils.isNotBlank(unit)) {
            quantity.setCode(unit);
        }
        setUcumSystemIfPresent(quantity);
        return quantity;
    }

    // resolveCanonicalPath now lives in CustomMapping

    private String durationStringFromQuantity(final Quantity quantity) {
        if (quantity == null || quantity.getValue() == null) {
            return null;
        }
        String unit = StringUtils.isNotBlank(quantity.getCode()) ? quantity.getCode() : quantity.getUnit();
        if (StringUtils.isBlank(unit)) {
            return null;
        }
        Timing.UnitsOfTime u = unitFromString(unit);
        if (u == null) {
            return null;
        }
        return durationString(quantity.getValue().doubleValue(), u);
    }

    private String unitToCode(final Timing.UnitsOfTime unit) {
        if (unit == null) {
            return null;
        }
        return switch (unit) {
            case S -> "s";
            case MIN -> "min";
            case H -> "h";
            case D -> "d";
            case WK -> "wk";
            case MO -> "mo";
            case A -> "a";
            default -> null;
        };
    }

    private DataWithIndex toFhirTimingDaily(final List<String> joinedValues,
                                                                final JsonObject valueHolder,
                                                                final Integer lastIndex,
                                                                final String path,
                                                                final OpenFhirMapperUtils mapperUtils) {
        return toFhirTiming(joinedValues, valueHolder, lastIndex, path, mapperUtils, true, true);
    }

    private DataWithIndex toFhirTimingNonDaily(final List<String> joinedValues,
                                                                   final JsonObject valueHolder,
                                                                   final Integer lastIndex,
                                                                   final String path,
                                                                   final OpenFhirMapperUtils mapperUtils) {
        return toFhirTiming(joinedValues, valueHolder, lastIndex, path, mapperUtils, true, true);
    }

    private DataWithIndex toFhirTiming(final List<String> joinedValues,
                                                           final JsonObject valueHolder,
                                                           final Integer lastIndex,
                                                           final String path,
                                                           final OpenFhirMapperUtils mapperUtils,
                                                           final boolean includeZeitpunkt,
                                                           final boolean inferPeriodFromFrequency) {
        FhirValueReaders readers = new FhirValueReaders(mapperUtils);
        List<String> timingValues = collectTimingValues(joinedValues, valueHolder, path);
        Timing timing = new Timing();
        Timing.TimingRepeatComponent repeat = new Timing.TimingRepeatComponent();

        if (includeZeitpunkt) {
            String timePath = find(timingValues, "zeitpunkt");
            if (timePath != null) {
                String time = readers.get(valueHolder, timePath);
                if (StringUtils.isNotBlank(time)) {
                    repeat.addTimeOfDay(time);
                }
            }
        }
        String eventPath = find(timingValues, "bestimmtes_ereignis:0/ereignis");
        if (eventPath == null) {
            eventPath = timingValues.stream()
                    .filter(v -> v.contains("/bestimmtes_ereignis:") && v.endsWith("/ereignis"))
                    .findFirst()
                    .orElse(null);
        }
        if (eventPath != null) {
            String eventCode = readers.get(valueHolder, eventPath);
            if (StringUtils.isNotBlank(eventCode)) {
                try {
                    repeat.addWhen(Timing.EventTiming.fromCode(eventCode));
                } catch (Exception ignored) {
                    // Ignore non-FHIR event timing codes.
                }
            }
        }

        TimingFlatMapper.FrequencyValue freq = TimingFlatMapper.readFrequency(valueHolder, timingValues, readers);
        if (freq != null && freq.frequency != null) {
            setFrequency(repeat, freq.frequency, freq.frequencyMax);
            if (inferPeriodFromFrequency) {
                Timing.UnitsOfTime unit = periodUnitFromFrequency(freq.unit);
                if (unit != null) {
                    if (!repeat.hasPeriodUnit()) {
                        repeat.setPeriodUnit(unit);
                    }
                    if (!repeat.hasPeriod()) {
                        repeat.setPeriod(1d);
                    }
                }
            }
        }

        TimingFlatMapper.DurationValue duration = TimingFlatMapper.readDuration(valueHolder, timingValues, readers);
        if (duration != null && StringUtils.isNotBlank(duration.value)) {
            DurationParts start = parseIsoDuration(duration.value);
            if (start != null) {
                repeat.setPeriod(start.value);
                repeat.setPeriodUnit(start.unit);
            }
            if (StringUtils.isNotBlank(duration.valueMax)) {
                DurationParts end = parseIsoDuration(duration.valueMax);
                if (end != null && start != null && end.unit == start.unit && !end.value.equals(start.value)) {
                    repeat.setPeriodMax(end.value);
                }
            }
        }

        if (!repeat.isEmpty()) {
            timing.setRepeat(repeat);
            return new DataWithIndex(
                    timing,
                    -1,
                    timingAnchorPath(path), null);
        }
        return null;
    }

    private List<String> collectTimingValues(final List<String> joinedValues,
                                             final JsonObject valueHolder,
                                             final String path) {
        List<String> values = new ArrayList<>();
        if (valueHolder != null && StringUtils.isNotBlank(path)) {
            String prefix = path.endsWith("/") ? path : path + "/";
            String normalizedPrefix = removeRecurring(prefix);
            for (String key : valueHolder.keySet()) {
                if (key.startsWith(prefix) || removeIndexes(key).startsWith(normalizedPrefix)) {
                    values.add(key);
                }
            }
        }
        if (joinedValues != null) {
            for (String joinedValue : joinedValues) {
                if (!values.contains(joinedValue)) {
                    values.add(joinedValue);
                }
            }
        }
        return values;
    }

    private String removeIndexes(final String path) {
        if (path == null) {
            return null;
        }
        return path.replaceAll(":\\d+", "");
    }

    private String removeRecurring(final String path) {
        if (path == null) {
            return null;
        }
        return path.replaceAll(RECURRING_SYNTAX_ESCAPED, "");
    }

    private String timingAnchorPath(final String path) {
        if (StringUtils.isBlank(path)) {
            return path;
        }
        String anchored = path.replaceAll("/tägliche_dosierung:\\d+.*$", "/art_der_verabreichung");
        if (!anchored.equals(path)) {
            return anchored;
        }
        anchored = path.replaceAll("/nicht_tägliche_dosierung:\\d+.*$", "/art_der_verabreichung");
        return anchored;
    }

    private DataWithIndex toFhirTimingRepeat(final List<String> joinedValues,
                                                                 final JsonObject valueHolder,
                                                                 final Integer lastIndex,
                                                                 final String path,
                                                                 final OpenFhirMapperUtils mapperUtils) {
        FhirValueReaders readers = new FhirValueReaders(mapperUtils);
        TimingFlatMapper.DurationValue duration = TimingFlatMapper.readDuration(valueHolder, joinedValues, readers);
        if ((duration == null || StringUtils.isBlank(duration.value))
                && valueHolder != null
                && StringUtils.isNotBlank(path)) {
            List<String> scoped = valueHolder.keySet().stream()
                    .filter(k -> k.startsWith(path + "/") || k.startsWith(path + "|"))
                    .toList();
            if (!scoped.isEmpty()) {
                duration = TimingFlatMapper.readDuration(valueHolder, scoped, readers);
            }
        }
        if ((duration == null || StringUtils.isBlank(duration.value))
                && valueHolder != null
                && StringUtils.isNotBlank(path)) {
            String direct = readers.get(valueHolder, path);
            if (StringUtils.isBlank(direct)) {
                direct = readers.get(valueHolder, path + "|value");
            }
            if (StringUtils.isBlank(direct)) {
                direct = readers.get(valueHolder, path + "/duration_value|value");
            }
            if (StringUtils.isNotBlank(direct)) {
                duration = new TimingFlatMapper.DurationValue(direct, null);
            }
        }
        if (duration == null || StringUtils.isBlank(duration.value)) {
            return null;
        }
        Timing.TimingRepeatComponent repeat = new Timing.TimingRepeatComponent();
        parseDurationValue(duration.value, repeat);
        if (StringUtils.isNotBlank(duration.valueMax)) {
            DurationParts start = parseIsoDuration(duration.value);
            DurationParts end = parseIsoDuration(duration.valueMax);
            if (start != null && end != null && end.unit == start.unit && !end.value.equals(start.value)) {
                repeat.setDurationMax(end.value);
            }
        }
        return repeat.isEmpty() ? null : new DataWithIndex(repeat, lastIndex == null ? -1 : lastIndex, path, null);
    }

    private Quantity readQuantityForSide(FhirValueReaders readers, JsonObject valueHolder,
                                         List<String> joinedValues, String side) {
        String prefix = StringUtils.isBlank(side) ? "" : side + "|";
        String magnitudePath = find(joinedValues, prefix + "magnitude");
        String unitPath = find(joinedValues, prefix + "unit");
        String codePath = find(joinedValues, prefix + "code");
        String valuePath = find(joinedValues, prefix + "value");

        Quantity q = new Quantity();
        boolean populated = false;

        if (magnitudePath != null) {
            Object n = readers.number(readers.get(valueHolder, magnitudePath));
            if (n instanceof Number) {
                q.setValue(((Number) n).doubleValue());
                populated = true;
            }
        }
        if (unitPath != null) {
            String unit = readers.get(valueHolder, unitPath);
            if (StringUtils.isNotBlank(unit)) {
                q.setUnit(unit);
                populated = true;
            }
        }
        if (valuePath != null) {
            String unit = readers.get(valueHolder, valuePath);
            if (StringUtils.isNotBlank(unit)) {
                q.setUnit(unit);
                populated = true;
            }
        }
        if (codePath != null) {
            String code = readers.get(valueHolder, codePath);
            if (StringUtils.isNotBlank(code)) {
                q.setCode(code);
                populated = true;
            }
        }

        if (populated) {
            setUcumSystemIfPresent(q);
            return q;
        }
        return null;
    }

    private void setUcumSystemIfPresent(Quantity quantity) {
        if (quantity == null) {
            return;
        }
        if (quantity.hasSystem()) {
            return;
        }
        if (StringUtils.isNotBlank(quantity.getCode()) || StringUtils.isNotBlank(quantity.getUnit())) {
            quantity.setSystem("http://unitsofmeasure.org");
        }
    }

    private String serializeRange(final Range range) {
        if (range == null) {
            return null;
        }
        Quantity low = range.getLow();
        Quantity high = range.getHigh();
        if ((low == null || low.getValue() == null) && (high == null || high.getValue() == null)) {
            return null;
        }

        String lowUnit = normalizeUcumUnit(bestUnit(low));
        String highUnit = normalizeUcumUnit(bestUnit(high));

        if (low != null && low.getValue() != null && high != null && high.getValue() != null
                && StringUtils.isNotBlank(lowUnit) && lowUnit.equals(highUnit)) {
            return stripTrailingZeros(low.getValue().doubleValue()) + "-" + stripTrailingZeros(high.getValue().doubleValue()) + " " + lowUnit;
        }

        String lowPart = formatQuantityText(low);
        String highPart = formatQuantityText(high);

        if (StringUtils.isNotBlank(lowPart) && StringUtils.isNotBlank(highPart)) {
            return lowPart + " - " + highPart;
        }
        if (StringUtils.isNotBlank(lowPart)) {
            return ">= " + lowPart;
        }
        if (StringUtils.isNotBlank(highPart)) {
            return "<= " + highPart;
        }
        return null;
    }

    private String formatQuantityText(final Quantity quantity) {
        if (quantity == null || quantity.getValue() == null) {
            return null;
        }
        String unit = normalizeUcumUnit(bestUnit(quantity));
        String value = stripTrailingZeros(quantity.getValue().doubleValue());
        if (StringUtils.isBlank(unit)) {
            return value;
        }
        return value + " " + unit;
    }

    private String bestUnit(final Quantity quantity) {
        if (quantity == null) {
            return null;
        }
        if (StringUtils.isNotBlank(quantity.getUnit())) {
            return quantity.getUnit().trim();
        }
        if (StringUtils.isNotBlank(quantity.getCode())) {
            return quantity.getCode().trim();
        }
        return null;
    }

    private Range parseRangeText(final String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String value = text.trim();

        if (value.startsWith(">=")) {
            Quantity low = parseQuantityText(value.substring(2).trim());
            if (low == null) return null;
            Range range = new Range();
            range.setLow(low);
            return range;
        }
        if (value.startsWith("<=")) {
            Quantity high = parseQuantityText(value.substring(2).trim());
            if (high == null) return null;
            Range range = new Range();
            range.setHigh(high);
            return range;
        }

        String[] parts = value.split("\\s*-\\s*", 2);
        if (parts.length == 2) {
            Quantity left = parseQuantityText(parts[0]);
            Quantity right = parseQuantityText(parts[1]);

            if (left != null && right != null) {
                String leftUnit = bestUnit(left);
                String rightUnit = bestUnit(right);
                if (StringUtils.isBlank(leftUnit) && StringUtils.isNotBlank(rightUnit)) {
                    left.setUnit(rightUnit);
                    left.setCode(rightUnit);
                    setUcumSystemIfPresent(left);
                } else if (StringUtils.isBlank(rightUnit) && StringUtils.isNotBlank(leftUnit)) {
                    right.setUnit(leftUnit);
                    right.setCode(leftUnit);
                    setUcumSystemIfPresent(right);
                }
                Range range = new Range();
                range.setLow(left);
                range.setHigh(right);
                return range;
            }

            // Support compact format like "150-300 mL/h".
            Double lowValue = parseDouble(parts[0].trim().replace(',', '.'));
            Quantity rightWithUnit = parseQuantityText(parts[1]);
            if (lowValue != null && rightWithUnit != null && rightWithUnit.getValue() != null) {
                Quantity low = new Quantity();
                low.setValue(lowValue);
                if (StringUtils.isNotBlank(rightWithUnit.getUnit())) {
                    low.setUnit(rightWithUnit.getUnit());
                    low.setCode(rightWithUnit.getUnit());
                }
                setUcumSystemIfPresent(low);

                Range range = new Range();
                range.setLow(low);
                range.setHigh(rightWithUnit);
                return range;
            }
            return null;
        }

        // Single quantity is interpreted as lower bound.
        Quantity single = parseQuantityText(value);
        if (single == null) {
            return null;
        }
        Range range = new Range();
        range.setLow(single);
        return range;
    }

    private Quantity parseQuantityText(final String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String normalized = text.trim().replace(',', '.');
        String[] parts = normalized.split("\\s+", 2);
        Double magnitude = parseDouble(parts[0]);
        if (magnitude == null) {
            return null;
        }
        Quantity quantity = new Quantity();
        quantity.setValue(magnitude);
        if (parts.length > 1 && StringUtils.isNotBlank(parts[1])) {
            String unit = normalizeUcumUnit(parts[1]);
            quantity.setUnit(unit);
            quantity.setCode(unit);
        }
        setUcumSystemIfPresent(quantity);
        return quantity;
    }

    private String buildUnit(String numeratorUnit, String denominatorUnit) {
        if (StringUtils.isBlank(numeratorUnit)) {
            return null;
        }
        if (StringUtils.isBlank(denominatorUnit)) {
            return numeratorUnit;
        }
        return numeratorUnit + "/" + denominatorUnit;
    }

    private String unitOrCodePreferCode(final Quantity quantity) {
        if (quantity == null) {
            return null;
        }
        if (StringUtils.isNotBlank(quantity.getCode())) {
            return quantity.getCode().trim();
        }
        if (StringUtils.isNotBlank(quantity.getUnit())) {
            return quantity.getUnit().trim();
        }
        return null;
    }

    private boolean isAllowedRateUnit(final String unit) {
        if (StringUtils.isBlank(unit)) {
            return false;
        }
        return ALLOWED_RATE_UNITS.contains(normalizeUcumUnit(unit));
    }

    private String normalizeUcumUnit(final String unit) {
        if (StringUtils.isBlank(unit)) {
            return unit;
        }
        return unit.toLowerCase(Locale.ROOT).trim();
    }

    private String toFrequencyUnit(Timing.UnitsOfTime unit) {
        if (unit == null) return null;
        return switch (unit.toCode()) {
            case "d" -> "1/d";
            case "h" -> "1/h";
            case "min" -> "1/min";
            case "s" -> "1/s";
            default -> null;
        };
    }

    private String buildDurationFromPeriod(Double period, Double periodMax, Timing.UnitsOfTime unit) {
        if (period == null || unit == null) {
            return null;
        }
        String base = durationString(period, unit);
        if (StringUtils.isBlank(base)) {
            return null;
        }
        if (periodMax != null && periodMax > 0 && !periodMax.equals(period)) {
            String max = durationString(periodMax, unit);
            if (StringUtils.isNotBlank(max)) {
                return base + "-" + max;
            }
        }
        return base;
    }

    private String durationString(Double value, Timing.UnitsOfTime unit) {
        if (value == null || unit == null) {
            return null;
        }
        String code = unit.toCode();
        if (!isAllowedPeriodUnit(code)) {
            log.debug("Unsupported periodUnit {}", code);
            return null;
        }
        String v = stripTrailingZeros(value);
        return switch (code) {
            case "d" -> "P" + v + "D";
            case "h" -> "PT" + v + "H";
            case "min" -> "PT" + v + "M";
            case "s" -> "PT" + v + "S";
            default -> null;
        };
    }

    private boolean isAllowedPeriodUnit(String code) {
        if (code == null) return false;
        return switch (code) {
            case "d", "h", "min", "s" -> true;
            default -> false;
        };
    }

    private String stripTrailingZeros(Double value) {
        if (value == null) return null;
        String s = String.format(Locale.ROOT, "%s", value);
        if (s.contains(".")) {
            s = s.replaceAll("\\.?0+$", "");
        }
        return s;
    }

    private String find(List<String> joinedValues, String suffix) {
        if (joinedValues == null) return null;
        return joinedValues.stream().filter(s -> s.endsWith(suffix)).findFirst().orElse(null);
    }

    private String findInValueHolder(JsonObject valueHolder, String suffix) {
        if (valueHolder == null || valueHolder.keySet() == null) return null;
        return valueHolder.keySet().stream().filter(s -> s.endsWith(suffix)).findFirst().orElse(null);
    }

    private String findAnyInValueHolder(final JsonObject valueHolder, final String... suffixes) {
        if (suffixes == null) {
            return null;
        }
        for (String suffix : suffixes) {
            String found = findInValueHolder(valueHolder, suffix);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void setFrequency(Timing.TimingRepeatComponent repeat, Double frequency, Double frequencyMax) {
        if (frequency == null) return;
        int freqVal = (int) Math.round(frequency);
        repeat.setFrequency(freqVal);
        if (frequencyMax != null && !frequencyMax.equals(frequency)) {
            int maxVal = (int) Math.round(frequencyMax);
            repeat.setFrequencyMax(maxVal);
        }
    }

    private void parseDurationValue(String text, Timing.TimingRepeatComponent repeat) {
        if (StringUtils.isBlank(text)) return;
        String trimmed = text.trim();
        if (!trimmed.startsWith("P")) {
            return;
        }
        DurationParts start = parseIsoDuration(trimmed);
        if (start == null) {
            return;
        }
        repeat.setDuration(start.value);
        repeat.setDurationUnit(start.unit);
        if (trimmed.contains("-")) {
            String[] parts = trimmed.split("-", 2);
            DurationParts end = parseIsoDuration(parts[1]);
            if (end != null && end.unit == start.unit && !end.value.equals(start.value)) {
                repeat.setDurationMax(end.value);
            }
        }
    }


    private Timing.UnitsOfTime unitFromCode(String code) {
        if (code == null) return null;
        return switch (code) {
            case "d" -> Timing.UnitsOfTime.D;
            case "h" -> Timing.UnitsOfTime.H;
            case "min" -> Timing.UnitsOfTime.MIN;
            case "s" -> Timing.UnitsOfTime.S;
            case "wk", "w", "week", "weeks" -> Timing.UnitsOfTime.WK;
            case "mo", "mon", "month", "months" -> Timing.UnitsOfTime.MO;
            case "a", "y", "yr", "year", "years" -> Timing.UnitsOfTime.A;
            case "day", "days" -> Timing.UnitsOfTime.D;
            case "hour", "hours" -> Timing.UnitsOfTime.H;
            case "minute", "minutes" -> Timing.UnitsOfTime.MIN;
            case "second", "seconds" -> Timing.UnitsOfTime.S;
            default -> null;
        };
    }

    private Timing.UnitsOfTime unitFromString(String unit) {
        if (StringUtils.isBlank(unit)) return null;
        String normalized = unit.toLowerCase(Locale.ROOT).trim();
        return unitFromCode(normalized);
    }

    private Timing.UnitsOfTime periodUnitFromFrequency(String unit) {
        if (StringUtils.isBlank(unit)) return null;
        String normalized = unit.toLowerCase(Locale.ROOT).trim();
        if (normalized.startsWith("1/")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return unitFromCode(normalized);
    }

    private Timing.UnitsOfTime extractUnitsOfTime(Object unitObj) {
        if (unitObj == null) {
            return null;
        }
        if (unitObj instanceof Timing.UnitsOfTime u) {
            return u;
        }
        if (unitObj instanceof org.hl7.fhir.r4.model.Enumeration<?> e) {
            Object v = e.getValue();
            if (v instanceof Timing.UnitsOfTime u) {
                return u;
            }
        }
        return null;
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return null;
        }
    }

    private DurationParts parseIsoDuration(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.contains("-")) {
            trimmed = trimmed.split("-", 2)[0];
        }
        if (!trimmed.startsWith("P")) {
            return null;
        }
        DurationParts compoundParts = parseCompoundIsoDuration(trimmed);
        if (compoundParts != null) {
            return compoundParts;
        }
        String val;
        char unitChar = trimmed.charAt(trimmed.length() - 1);
        boolean timeBased = trimmed.startsWith("PT");
        if (trimmed.startsWith("PT")) {
            val = trimmed.substring(2, trimmed.length() - 1);
        } else {
            val = trimmed.substring(1, trimmed.length() - 1);
        }
        Double value = parseDouble(val);
        Timing.UnitsOfTime unit = unitFromIso(unitChar, timeBased);
        if (value == null || unit == null) {
            return null;
        }
        return new DurationParts(value, unit);
    }

    private DurationParts parseCompoundIsoDuration(final String text) {
        Matcher matcher = ISO_DAY_TIME_DURATION.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        Double days = parseDouble(matcher.group(1));
        Double hours = parseDouble(matcher.group(2));
        Double minutes = parseDouble(matcher.group(3));
        Double seconds = parseDouble(matcher.group(4));
        if (days == null && hours == null && minutes == null && seconds == null) {
            return null;
        }

        double d = days == null ? 0d : days;
        double h = hours == null ? 0d : hours;
        double m = minutes == null ? 0d : minutes;
        double s = seconds == null ? 0d : seconds;

        // Preserve day-only durations as days.
        if (d > 0d && h == 0d && m == 0d && s == 0d) {
            return new DurationParts(d, Timing.UnitsOfTime.D);
        }

        double totalSeconds = (d * 86400d) + (h * 3600d) + (m * 60d) + s;
        if (totalSeconds <= 0d) {
            return null;
        }
        if (totalSeconds >= 3600d) {
            return new DurationParts(totalSeconds / 3600d, Timing.UnitsOfTime.H);
        }
        if (totalSeconds >= 60d) {
            return new DurationParts(totalSeconds / 60d, Timing.UnitsOfTime.MIN);
        }
        return new DurationParts(totalSeconds, Timing.UnitsOfTime.S);
    }

    private Timing.UnitsOfTime unitFromIso(char unit, boolean timeBased) {
        if (timeBased) {
            return switch (unit) {
                case 'H' -> Timing.UnitsOfTime.H;
                case 'M' -> Timing.UnitsOfTime.MIN;
                case 'S' -> Timing.UnitsOfTime.S;
                default -> null;
            };
        }
        return switch (unit) {
            case 'D' -> Timing.UnitsOfTime.D;
            case 'W' -> Timing.UnitsOfTime.WK;
            case 'M' -> Timing.UnitsOfTime.MO;
            case 'Y' -> Timing.UnitsOfTime.A;
            default -> null;
        };
    }

    private boolean isDailyPeriodUnit(String code) {
        if (code == null) return false;
        return switch (code) {
            case "d", "h", "min", "s" -> true;
            default -> false;
        };
    }

    private boolean isNonDailyPeriodUnit(String code) {
        if (code == null) return false;
        return switch (code) {
            case "d", "wk", "mo", "a" -> true;
            default -> false;
        };
    }

    private static final class DurationParts {
        private final Double value;
        private final Timing.UnitsOfTime unit;

        private DurationParts(Double value, Timing.UnitsOfTime unit) {
            this.value = value;
            this.unit = unit;
        }
    }
}
