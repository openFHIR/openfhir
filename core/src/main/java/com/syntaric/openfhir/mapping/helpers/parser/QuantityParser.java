package com.syntaric.openfhir.mapping.helpers.parser;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Quantity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QuantityParser {

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

    public DataWithIndex proportion(List<String> joinedValues,
                                    JsonObject valueHolder,
                                    Integer lastIndex,
                                    String path) {

        String numeratorPath = path + "|numerator";
        String denominatorPath = path + "|denominator";

        String numeratorValue = fhirValueReaders.get(valueHolder, numeratorPath);
        String denominatorValue = fhirValueReaders.get(valueHolder, denominatorPath);
        if (StringUtils.isAllBlank(numeratorValue, denominatorValue) && !path.contains("/" + FhirConnectConst.LEAF_TYPE_PROPORTION_VALUE)) {
            return proportion(joinedValues, valueHolder, lastIndex, path + "/" + FhirConnectConst.LEAF_TYPE_PROPORTION_VALUE);
        }

        Quantity q = new Quantity();

        String denom = fhirValueReaders.get(valueHolder, denominatorPath);
        if ("100.0".equals(denom)) {
            q.setCode("%");
            q.setUnit("percent");
            q.setSystem("http://unitsofmeasure.org");
        }

        Object numVal = fhirValueReaders.number(fhirValueReaders.get(valueHolder, numeratorPath));
        if (numVal instanceof Long l) q.setValue(l);
        if (numVal instanceof Double d) q.setValue(d);

        return new DataWithIndex(q, lastIndex, path, FhirConnectConst.DV_PROPORTION);
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
