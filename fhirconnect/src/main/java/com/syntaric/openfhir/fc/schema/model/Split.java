package com.syntaric.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.syntaric.openfhir.fc.schema.model.SplitModel;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "fhir",
        "openehr"
})
@Data
public class Split {

    @JsonProperty("fhir")
    private SplitModel fhir;

    @JsonProperty("openehr")
    private SplitModel openehr;
}
