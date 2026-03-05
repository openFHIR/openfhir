package com.syntaric.openfhir.fc.schema.terminology;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "system",
        "code",
        "display"
})
@Data
public class TerminologyMappingsSystemCode {
    @JsonProperty("system")
    private String system;

    @JsonProperty("code")
    private String code;

    @JsonProperty("display")
    private String display;

    @JsonProperty("ordinal")
    private String ordinal;
}
