package com.syntaric.openfhir.mapping.helpers.parser;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QuantityParser {

    /**
     * Carries a DV_PROPORTION denominator that FHIR has no field for. A percent needs no extension:
     * its denominator is implied by the UCUM code {@code %}.
     */
    public static final String PROPORTION_DENOMINATOR_EXTENSION =
            "http://openfhir.org/StructureDefinition/proportion-denominator";

    /** Carries the openEHR PROPORTION_KIND ordinal, which no FHIR field expresses. */
    public static final String PROPORTION_KIND_EXTENSION =
            "http://openfhir.org/StructureDefinition/proportion-kind";

    private final FhirValueReaders fhirValueReaders;

    @Autowired
    public QuantityParser(FhirValueReaders readers) {
        this.fhirValueReaders = readers;
    }

    public DataWithIndex count(JsonObject valueHolder, Integer lastIndex, String path) {
        String raw = fhirValueReaders.get(valueHolder, path);
        if (raw == null && !path.contains("/" + FhirConnectConst.LEAF_TYPE_COUNT_VALUE)) {
            return count(valueHolder, lastIndex, path + "/" + FhirConnectConst.LEAF_TYPE_COUNT_VALUE);
        }
        return new DataWithIndex(new IntegerType(raw), lastIndex, path, FhirConnectConst.DV_COUNT);
    }

    /**
     * A DV_PROPORTION becomes a FHIR {@code Quantity} holding the numerator.
     * <p>
     * Only a percent has a faithful FHIR representation: denominator 100 maps to the UCUM code
     * {@code %}, and the numerator then reads correctly as-is. Every other kind of proportion has a
     * denominator FHIR cannot express, so it is preserved on an extension rather than dropped —
     * without it the numerator alone is meaningless (a 3/4 ratio would arrive as a bare "3").
     * <p>
     * The openEHR {@code |type} is recorded on the same extension. It is a PROPORTION_KIND
     * constraint rather than a label, and the reverse leg can only re-derive it from the
     * denominator, which cannot distinguish a fraction from an integer fraction — so carrying it
     * explicitly is what lets those kinds survive a round trip.
     */
    public DataWithIndex proportion(List<String> joinedValues,
                                    JsonObject valueHolder,
                                    Integer lastIndex,
                                    String path) {

        String numeratorPath = path + "|numerator";
        String denominatorPath = path + "|denominator";
        String typePath = path + "|type";

        String numeratorValue = fhirValueReaders.get(valueHolder, numeratorPath);
        String denominatorValue = fhirValueReaders.get(valueHolder, denominatorPath);
        if (StringUtils.isAllBlank(numeratorValue, denominatorValue) && !path.contains("/" + FhirConnectConst.LEAF_TYPE_PROPORTION_VALUE)) {
            return proportion(joinedValues, valueHolder, lastIndex, path + "/" + FhirConnectConst.LEAF_TYPE_PROPORTION_VALUE);
        }

        Quantity q = new Quantity();

        Object denomVal = fhirValueReaders.number(denominatorValue);
        boolean isPercent = denomVal instanceof Long l && l == 100L
                            || denomVal instanceof Double d && d == 100.0d;
        if (isPercent) {
            q.setCode("%");
            q.setUnit("percent");
            q.setSystem("http://unitsofmeasure.org");
        } else if (denomVal != null) {
            q.addExtension(PROPORTION_DENOMINATOR_EXTENSION, toDecimal(denomVal));
        }

        String typeValue = fhirValueReaders.get(valueHolder, typePath);
        if (StringUtils.isNotBlank(typeValue)) {
            q.addExtension(PROPORTION_KIND_EXTENSION, new StringType(typeValue));
        }

        Object numVal = fhirValueReaders.number(numeratorValue);
        if (numVal instanceof Long l) q.setValue(l);
        if (numVal instanceof Double d) q.setValue(d);

        return new DataWithIndex(q, lastIndex, path, FhirConnectConst.DV_PROPORTION);
    }

    private static DecimalType toDecimal(Object number) {
        if (number instanceof Long l) return new DecimalType(l);
        return new DecimalType((Double) number);
    }

    public DataWithIndex quantity(List<String> joinedValues,
                                  JsonObject valueHolder,
                                  Integer lastIndex,
                                  String path) {

        String magnitudePath = path + "|magnitude";
        String unitPath = path + "|unit";
        String codePath = path + "|code";
        String valuePath = path + "|value";
        String ordinalPath = path + "|ordinal";

        String magnitudeValue = fhirValueReaders.get(valueHolder, magnitudePath);
        String unitValue = fhirValueReaders.get(valueHolder, unitPath);
        String codeValue = fhirValueReaders.get(valueHolder, codePath);
        String valueValue = fhirValueReaders.get(valueHolder, valuePath);
        String ordinalValue = fhirValueReaders.get(valueHolder, ordinalPath);

        if (StringUtils.isAllBlank(magnitudeValue, unitValue, codeValue, valueValue, ordinalValue) && !path.contains("/" + FhirConnectConst.LEAF_TYPE_QUANTITY_VALUE)) {
            return quantity(joinedValues, valueHolder, lastIndex, path + "/" + FhirConnectConst.LEAF_TYPE_QUANTITY_VALUE);
        }

        Quantity q = new Quantity();

        setQuantityValue(valueHolder, q, magnitudePath, ordinalPath);

        if (unitValue != null) {
            q.setUnit(unitValue);
        } else {
            q.setUnit(valueValue);
        }

        q.setCode(codeValue);

        if (StringUtils.isBlank(q.getCode()) && StringUtils.isNotBlank(q.getUnit())) {
            q.setCode(q.getUnit());
        }
        if (StringUtils.isBlank(q.getSystem()) && (StringUtils.isNotBlank(q.getCode()) || StringUtils.isNotBlank(q.getUnit()))) {
            // openEHR DV_QUANTITY defaults to UCUM when no unit system is explicitly carried.
            q.setSystem("http://unitsofmeasure.org");
        }

        // fallback if no extra fields are present
        if (magnitudePath == null && ordinalPath == null && unitPath == null && valuePath == null && codePath == null) {
            Object n = fhirValueReaders.number(fhirValueReaders.get(valueHolder, path));
            if (n instanceof Long l) q.setValue(l);
            if (n instanceof Double d) q.setValue(d);
        }

        return new DataWithIndex(q, lastIndex, path, FhirConnectConst.DV_QUANTITY);
    }

    private void setQuantityValue(JsonObject valueHolder, Quantity q, String magnitudePath, String ordinalPath) {
        if (magnitudePath != null) {
            Object n = fhirValueReaders.number(fhirValueReaders.get(valueHolder, magnitudePath));
            if (n instanceof Long l) q.setValue(l);
            if (n instanceof Double d) q.setValue(d);
            return;
        }
        if (ordinalPath != null) {
            Object n = fhirValueReaders.number(fhirValueReaders.get(valueHolder, ordinalPath));
            if (n instanceof Long l) q.setValue(l);
            if (n instanceof Double d) q.setValue(d);
        }
    }

    private String find(List<String> joinedValues, String suffix) {
        return joinedValues.stream().filter(s -> s.endsWith(suffix)).findFirst().orElse(null);
    }
}
