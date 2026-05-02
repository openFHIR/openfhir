package com.syntaric.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "meaning",
        "type"
})
public class Link implements Serializable {
    @JsonProperty("meaning")
    private String meaning;
    @JsonProperty("type")
    private String type;

    public Link copy() {
        final Link link = new Link();
        link.setMeaning(meaning);
        link.setType(type);
        return link;
    }

    @JsonProperty("meaning")
    public String getMeaning() {
        return meaning;
    }

    @JsonProperty("meaning")
    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }
}

