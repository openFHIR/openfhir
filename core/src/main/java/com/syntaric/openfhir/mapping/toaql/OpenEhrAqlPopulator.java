package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.fc.FhirConnectConst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class OpenEhrAqlPopulator {

    public String getDataTypeAwareAqlSuffix(final String expectedValue,
                                            final List<String> possibleRmTypes) {
        final String rmType = possibleRmTypes.get(0); // todo: amend this
        switch (rmType) {
            case FhirConnectConst.DV_QUANTITY:
                return String.format("/value = %s", expectedValue);

            // todo: add others
            default:
                return String.format("/value = '%s'", expectedValue);
        }
    }
}

