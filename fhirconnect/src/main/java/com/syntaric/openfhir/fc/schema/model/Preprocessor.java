package com.syntaric.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.io.Serializable;
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
public class Preprocessor implements Serializable {

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
