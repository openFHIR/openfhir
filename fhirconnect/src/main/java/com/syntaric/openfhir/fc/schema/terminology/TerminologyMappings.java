package com.syntaric.openfhir.fc.schema.terminology;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.syntaric.openfhir.fc.schema.terminology.TerminologyMappingsSystemCode;
import java.io.Serializable;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "type",
        "mappings"
})
@Data
public class TerminologyMappings implements Serializable {
    @JsonProperty("openehr")
    private TerminologyMappingsSystemCode openehr;

    @JsonProperty("fhir")
    private TerminologyMappingsSystemCode fhir;

    public TerminologyMappings copy() {
        final TerminologyMappings terminologyMappings = new TerminologyMappings();
        terminologyMappings.setFhir(fhir);
        terminologyMappings.setOpenehr(openehr);
        return terminologyMappings;
    }
}
