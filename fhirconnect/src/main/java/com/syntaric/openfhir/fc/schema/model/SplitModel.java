package com.syntaric.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "create",
        "path",
        "unique"
})
@Data
public class SplitModel {

    @JsonProperty("create")
    private String create;

    @JsonProperty("path")
    private String path;

    @JsonProperty("unique")
    private List<String> unique;
}
