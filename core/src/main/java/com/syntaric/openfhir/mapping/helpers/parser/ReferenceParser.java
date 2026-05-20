package com.syntaric.openfhir.mapping.helpers.parser;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReferenceParser {

    private final FhirValueReaders fhirValueReaders;

    @Autowired
    public ReferenceParser(FhirValueReaders readers) {
        this.fhirValueReaders = readers;
    }

    public DataWithIndex participant(List<String> joinedValues,
                                     JsonObject valueHolder,
                                     Integer lastIndex,
                                     String path,
                                     String originalOpenEhrPath) {
        final boolean isFunction = originalOpenEhrPath.endsWith("function");

        final Reference reference = new Reference();

        String namePath = path + "|name";
        String idPath = path + "|id";
        String assigner = path + "|identifiers_assigner:0";
        String namespace = path + "|id_namespace";
        String function = path + "|function";

        String nameValue = fhirValueReaders.get(valueHolder, namePath);
        String assignerValue = fhirValueReaders.get(valueHolder, assigner);
        String idValue = fhirValueReaders.get(valueHolder, idPath);
        String namespaceValue = fhirValueReaders.get(valueHolder, namespace);
        String functionValue = fhirValueReaders.get(valueHolder, function);

        if (isFunction) {
            return new DataWithIndex(new StringType(functionValue), lastIndex, path, FhirConnectConst.DV_TEXT);
        }

        if (StringUtils.isNotEmpty(idValue)) {
            if (idValue.startsWith("http://")) {
                reference.setReference(idValue);
            } else {
                reference.setIdentifier(new Identifier().setValue(idValue).setSystem(assignerValue == null ? namespaceValue : assignerValue));
            }
        }

        reference.setDisplay(nameValue);

        return new DataWithIndex(reference, lastIndex, path, FhirConnectConst.DV_PARTICIPATION);
    }
}
