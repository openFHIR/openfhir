package com.syntaric.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.List;

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

    public List<Condition> getFhirConditions() {
        if (fhirConditions == null && fhirCondition != null) {
            return List.of(fhirCondition);
        }
        return fhirConditions;
    }
}
