package com.syntaric.openfhir.fc.schema.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "type",
        "profile"
})
@Data
public class BundleMetadata implements Serializable {

    @JsonProperty("type")
    private String type;
    @JsonProperty("profile")
    private String profile;
    @JsonProperty("identifier_value")
    private String identifierValue;
    @JsonProperty("identifier_system")
    private String identifierSystem;
}
