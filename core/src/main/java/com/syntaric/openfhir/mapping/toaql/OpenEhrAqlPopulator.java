package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.fc.FhirConnectConst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OpenEhrAqlPopulator {

    public String getDataTypeAwareAqlSuffix(final String expectedValue,
                                            final String rmType) {
        switch (rmType) {
            case FhirConnectConst.DV_QUANTITY:
                return String.format("/value = %s", expectedValue);

            // todo: add others
            default:
                return String.format("/value = '%s'", expectedValue);
        }
    }
}

