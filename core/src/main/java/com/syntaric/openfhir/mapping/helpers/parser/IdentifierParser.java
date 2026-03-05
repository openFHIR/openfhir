package com.syntaric.openfhir.mapping.helpers.parser;

import com.google.gson.JsonObject;

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
        String idPath = find(joinedValues, "|id");

        Identifier identifier = new Identifier();
        String resolvedIdPath = StringUtils.isEmpty(idPath) ? (path + "/identifier_value|id") : idPath;
        String issuerPath = find(joinedValues, "|issuer");
        String resolvedIssuerPath = StringUtils.isEmpty(issuerPath) ? (path + "/identifier_value|issuer") : issuerPath;
        String system = normalizeIdentifierSystem(fhirValueReaders.get(valueHolder, resolvedIssuerPath));
        identifier.setValue(fhirValueReaders.get(valueHolder, resolvedIdPath));
        identifier.setSystem(system);

        String typePath = find(joinedValues, "|type");
        if (StringUtils.isNotBlank(typePath)) {
            String rawType = fhirValueReaders.get(valueHolder, typePath);
            if (StringUtils.isNotBlank(rawType)) {
                Coding typeCoding = parseSystemValueOrFallback(rawType, "http://openehr.org/identifier/type");
                identifier.getType().addCoding(typeCoding);
            }
        }

        String assignerPath = find(joinedValues, "|assigner");
        if (StringUtils.isNotBlank(assignerPath)) {
            String rawAssigner = fhirValueReaders.get(valueHolder, assignerPath);
            if (StringUtils.isNotBlank(rawAssigner)) {
                Reference assigner = new Reference();
                Identifier assignerIdentifier = new Identifier();
                if (StringUtils.contains(rawAssigner, "::")) {
                    String[] parts = rawAssigner.split("::", 2);
                    assignerIdentifier.setSystem(parts[0]);
                    assignerIdentifier.setValue(parts.length > 1 ? parts[1] : null);
                    assigner.setDisplay(parts.length > 1 ? parts[1] : null);
                } else {
                    assignerIdentifier.setSystem("http://openehr.org/identifier/assigner");
                    assignerIdentifier.setValue(rawAssigner);
                    assigner.setDisplay(rawAssigner);
                }
                assigner.setIdentifier(assignerIdentifier);
                identifier.setAssigner(assigner);
            }
        }

        return new DataWithIndex(identifier, lastIndex, path);
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
