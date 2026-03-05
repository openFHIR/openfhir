package com.syntaric.openfhir.mapping.helpers.parser;

import com.google.gson.JsonObject;

import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CodedParser {

    private final FhirValueReaders fhirValueReaders;

    @Autowired
    public CodedParser(FhirValueReaders readers) {
        this.fhirValueReaders = readers;
    }

    public DataWithIndex codeableConcept(List<String> joinedValues,
                                         JsonObject valueHolder,
                                         Integer lastIndex,
                                         String path) {
        String base = fhirValueReaders.basePath(path);

        CodeableConcept codeableConcept = new CodeableConcept();

        String fallbackValue = find(joinedValues, "value");
        String fallbackCode = find(joinedValues, "code");
        String fallbackTerminology = find(joinedValues, "terminology");
        String fallbackOrdinal = find(joinedValues, "ordinal");

        String text = fhirValueReaders.get(valueHolder, base, "value", fallbackValue);
        String code = fhirValueReaders.get(valueHolder, base, "code", fallbackCode);
        String systemRaw = fhirValueReaders.get(valueHolder, base, "terminology", fallbackTerminology);
        String system = fhirValueReaders.cleanVersionFromSystem(systemRaw);
        String version = fhirValueReaders.version(systemRaw);
        String ordinal = fhirValueReaders.get(valueHolder, base, "ordinal", fallbackOrdinal);

        if (StringUtils.isAllBlank(text, code, system, systemRaw, version, ordinal)
                && !path.endsWith("/coded_text_value")) {
            return codeableConcept(joinedValues, valueHolder, lastIndex, path + "/coded_text_value");
        }

        codeableConcept.setText(text);

        if (code != null || system != null) {
            Coding coding = new Coding(system, code, text);
            if (version != null) {
                coding.setVersion(version);
            }
            codeableConcept.addCoding(coding);
        }

        if (ordinal != null) {
            codeableConcept.setText(ordinal);
        }

        addMappings(valueHolder, base, codeableConcept);
        return new DataWithIndex(codeableConcept, lastIndex, base);
    }

    public DataWithIndex coding(List<String> joinedValues,
                                JsonObject valueHolder,
                                Integer lastIndex,
                                String path) {
        String base = fhirValueReaders.basePath(path);

        String fallbackValue = find(joinedValues, "value");
        String fallbackCode = find(joinedValues, "code");
        String fallbackTerminology = find(joinedValues, "terminology");

        String display = fhirValueReaders.get(valueHolder, base, "value", fallbackValue);
        String code = fhirValueReaders.get(valueHolder, base, "code", fallbackCode);
        String systemRaw = fhirValueReaders.get(valueHolder, base, "terminology", fallbackTerminology);
        String system = fhirValueReaders.cleanVersionFromSystem(systemRaw);
        String version = fhirValueReaders.version(systemRaw);

        // Avoid emitting redundant display values when openEHR text equals the code.
        if (display != null && code != null && display.equals(code)) {
            display = null;
        }

        Coding coding = new Coding(system, code, display);
        if (version != null) {
            coding.setVersion(version);
        }

        return new DataWithIndex(coding, lastIndex, base);
    }

    private void addMappings(JsonObject valueHolder, String basePath, CodeableConcept cc) {
        for (int i = 0; ; i++) {
            String prefix = basePath + "/_mapping:" + i;
            if (!fhirValueReaders.mappingExists(valueHolder, prefix)) {
                break;
            }

            String system = fhirValueReaders.cleanVersionFromSystem(
                    fhirValueReaders.get(valueHolder, prefix + "/target|terminology"));
            String code = fhirValueReaders.get(valueHolder, prefix + "/target|code");
            String display = fhirValueReaders.get(valueHolder, prefix + "/target|preferred_term");

            if (system != null || code != null) {
                cc.addCoding(new Coding(system, code, display));
            }
        }
    }

    private String find(List<String> joinedValues, String suffix) {
        return joinedValues.stream().filter(s -> s.endsWith(suffix)).findFirst().orElse(null);
    }
}
