package com.syntaric.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.syntaric.openfhir.fc.schema.model.HierarchyWith;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "with",
        "split"
})
@Data
public class Hierarchy {

    @JsonProperty("with")
    private HierarchyWith with;

    @JsonProperty("split")
    private Split split;
}
