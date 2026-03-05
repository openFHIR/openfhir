
package com.syntaric.openfhir.fc.schema.model;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Fhir Config
 * <p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "structureDefinition"
})

public class FhirConfig {

    /**
     * (Required)
     */
    @JsonProperty("structureDefinition")
    private String structureDefinition;

    public FhirConfig copy() {
        final FhirConfig fhirConfig = new FhirConfig();
        fhirConfig.setStructureDefinition(structureDefinition);
        return fhirConfig;
    }

    /**
     * (Required)
     */
    @JsonProperty("structureDefinition")
    public String getStructureDefinition() {
        return structureDefinition;
    }

    /**
     * (Required)
     */
    @JsonProperty("structureDefinition")
    public void setStructureDefinition(String structureDefinition) {
        this.structureDefinition = structureDefinition;
    }

    public FhirConfig withStructureDefinition(String archetype) {
        this.structureDefinition = archetype;
        return this;
    }

}
