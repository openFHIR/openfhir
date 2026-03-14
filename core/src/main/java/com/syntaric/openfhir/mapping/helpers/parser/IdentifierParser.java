package com.syntaric.openfhir.mapping.helpers.parser;

import com.google.gson.JsonObject;

import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import java.net.URI;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IdentifierParser {

    private final FhirValueReaders fhirValueReaders;

    @Autowired
    public IdentifierParser(FhirValueReaders readers) {
        this.fhirValueReaders = readers;
    }

    public DataWithIndex identifier(List<String> joinedValues,
                                    JsonObject valueHolder,
                                    Integer lastIndex,
                                    String path) {
        // try to find an explicit "|id" path from joinedValues

        Identifier identifier = new Identifier();

        String idPath = path + "|id";
        String issuerPath = path+ "|issuer";
        String typePath = path+ "|type";
        String assignerPath = path+ "|assigner";

        String issuerValue = fhirValueReaders.get(valueHolder, issuerPath);
        String idValue = fhirValueReaders.get(valueHolder, idPath);
        String typeValue = fhirValueReaders.get(valueHolder, typePath);
        String assignerValue = fhirValueReaders.get(valueHolder, assignerPath);

        if(StringUtils.isAllBlank(issuerValue, idValue, typeValue, assignerValue)) {
            return identifier(joinedValues, valueHolder, lastIndex, path + "/" + FhirConnectConst.LEAF_TYPE_IDENTIFIER_VALUE);
        }

        String system = normalizeIdentifierSystem(issuerValue);
        identifier.setValue(idValue);
        identifier.setSystem(system);

        if (StringUtils.isNotBlank(typePath)) {
            if (StringUtils.isNotBlank(typeValue)) {
                Coding typeCoding = parseSystemValueOrFallback(typeValue, "http://openehr.org/identifier/type");
                identifier.getType().addCoding(typeCoding);
            }
        }

        if (StringUtils.isNotBlank(assignerPath)) {
            if (StringUtils.isNotBlank(assignerValue)) {
                Reference assigner = new Reference();
                Identifier assignerIdentifier = new Identifier();
                if (StringUtils.contains(assignerValue, "::")) {
                    String[] parts = assignerValue.split("::", 2);
                    assignerIdentifier.setSystem(parts[0]);
                    assignerIdentifier.setValue(parts.length > 1 ? parts[1] : null);
                    assigner.setDisplay(parts.length > 1 ? parts[1] : null);
                } else {
                    assignerIdentifier.setSystem("http://openehr.org/identifier/assigner");
                    assignerIdentifier.setValue(assignerValue);
                    assigner.setDisplay(assignerValue);
                }
                assigner.setIdentifier(assignerIdentifier);
                identifier.setAssigner(assigner);
            }
        }

        return new DataWithIndex(identifier, lastIndex, path, FhirConnectConst.DV_IDENTIFIER);
    }

    private String find(List<String> joinedValues, String suffix) {
        return joinedValues.stream().filter(s -> s.endsWith(suffix)).findFirst().orElse(null);
    }

    private String normalizeIdentifierSystem(String system) {
        if (StringUtils.isBlank(system)) {
            return system;
        }
        if (system.startsWith("http://openehr.org/identifier")) {
            return system;
        }
        if (isUri(system)) {
            return system;
        }
        String trimmed = system;
        return "http://openehr.org/identifier/" + trimmed;
    }

    private boolean isUri(String value) {
        try {
            return new URI(value).isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }

    private Coding parseSystemValueOrFallback(String raw, String fallbackSystem) {
        Coding coding = new Coding();
        if (StringUtils.contains(raw, "::")) {
            String[] parts = raw.split("::", 2);
            coding.setSystem(parts[0]);
            coding.setCode(parts.length > 1 ? parts[1] : null);
        } else {
            coding.setSystem(fallbackSystem);
            coding.setCode(raw);
        }
        return coding;
    }
}
