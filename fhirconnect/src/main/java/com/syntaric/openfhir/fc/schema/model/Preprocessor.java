package com.syntaric.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "fhirCondition",
        "fhirConditions",
        "openehrCondition",
        "hierarchy",
})
@Data
public class Preprocessor {

    @JsonProperty("fhirConditions")
    private List<Condition> fhirConditions;

    @JsonProperty("fhirCondition")
    private Condition fhirCondition;

    @JsonProperty("openehrCondition")
    private Condition openehrCondition;

    @JsonProperty("hierarchy")
    private Hierarchy hierarchy;

}
