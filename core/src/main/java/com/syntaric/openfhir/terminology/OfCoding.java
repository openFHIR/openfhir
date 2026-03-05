package com.syntaric.openfhir.terminology;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfCoding {
    private String system;
    private String code;
    private String display;
}
