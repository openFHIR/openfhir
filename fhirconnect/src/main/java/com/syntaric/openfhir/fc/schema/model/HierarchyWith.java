package com.syntaric.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.io.Serializable;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "fhir",
        "openehr"
})
@Data
public class HierarchyWith implements Serializable {

    @JsonProperty("fhir")
    private String fhir;

    @JsonProperty("openehr")
    private String openehr;
}
