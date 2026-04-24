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
        final String rmType = possibleRmTypes.get(0); // amend this
        switch (rmType) {
            case FhirConnectConst.DV_QUANTITY:
                return String.format("/value = %s", expectedValue);

            case FhirConnectConst.DV_COUNT:
                return String.format("/value = %s", expectedValue);

            case FhirConnectConst.DV_ORDINAL:
                return String.format("/value = %s", expectedValue);

            case FhirConnectConst.DV_PROPORTION:
                return String.format("/numerator = %s", expectedValue);

            case FhirConnectConst.DV_BOOL:
                return String.format("/value = %s", expectedValue);

            case FhirConnectConst.DV_CODED_TEXT:
                return String.format("/defining_code/code_string = '%s'", expectedValue);

            case FhirConnectConst.DV_TEXT:
                return String.format("/value = '%s'", expectedValue);

            case FhirConnectConst.DV_DATE_TIME:
            case FhirConnectConst.DV_DATE:
            case FhirConnectConst.DV_TIME:
            case FhirConnectConst.DV_DURATION:
                return String.format("/value = '%s'", expectedValue);

            case FhirConnectConst.DV_IDENTIFIER:
                return String.format("/id = '%s'", expectedValue);

            default:
                return String.format("/value = '%s'", expectedValue);
        }
    }
}

