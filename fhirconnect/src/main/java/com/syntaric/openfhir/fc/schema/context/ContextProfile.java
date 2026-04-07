package com.syntaric.openfhir.fc.schema.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "url",
        "version"
})
@Data
public class ContextProfile implements Serializable {

    @JsonProperty("url")
    private String url;
    @JsonProperty("version")
    private String version;
}
