package com.syntaric.openfhir.mapping.helpers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hl7.fhir.r4.model.Base;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataWithIndex {
    private Base data;
    private int index;
    private String fullOpenEhrPath;
    private String detectedType;
}
