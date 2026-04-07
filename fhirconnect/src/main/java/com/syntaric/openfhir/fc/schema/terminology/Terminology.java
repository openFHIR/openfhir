package com.syntaric.openfhir.fc.schema.terminology;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.syntaric.openfhir.fc.schema.terminology.TerminologyMappings;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "type",
        "mappings",
        "server",
        "conceptmap"
})
@Data
public class Terminology implements Serializable {

    @JsonProperty("type")
    private String type;

    @JsonProperty("server")
    private String server;

    @JsonProperty("conceptmap")
    private String conceptmap;

    @JsonProperty("mappings")
    private List<TerminologyMappings> mappings;

    public Terminology doCopy() {
        final Terminology terminology = new Terminology();
        terminology.setType(type);
        terminology.setMappings(mappings == null ? null : mappings.stream().map(
                TerminologyMappings::copy).collect(Collectors.toList()));
        terminology.setServer(server);
        terminology.setConceptmap(conceptmap);
        return terminology;
    }
}
