package com.syntaric.openfhir.fc.schema.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "aql",
        "rules"
})
@Data
public class ContextQuery implements Serializable {

    @JsonProperty("aql")
    private String aql;

    @JsonProperty("rules")
    private List<String> rules;
}
