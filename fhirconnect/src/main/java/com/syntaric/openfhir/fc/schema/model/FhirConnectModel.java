
package com.syntaric.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.syntaric.openfhir.fc.schema.Metadata;
import com.syntaric.openfhir.fc.schema.SchemaType;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import java.util.List;
import lombok.Getter;
import lombok.Setter;


/**
 * FHIRConnect Context Schema
 * <p>
 *
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "grammar",
        "type",
        "metadata",
        "spec",
        "terminology"
})
public class FhirConnectModel {


    @Getter
    @Setter
    @JsonProperty("id")
    private String id;

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("grammar")
    private String grammar;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("type")
    private SchemaType type;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("metadata")
    private Metadata metadata;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("spec")
    private Spec spec;
    @JsonProperty("terminology")
    private Terminology terminology;

    @JsonProperty("preprocessor")
    @Getter
    @Setter
    private Preprocessor preprocessor;



    /**
     * Mappings
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("mappings")
    private List<Mapping> mappings;



    /**
     * Mappings
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("mappings")
    public List<Mapping> getMappings() {
        return mappings;
    }

    /**
     * Mappings
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("mappings")
    public void setMappings(List<Mapping> mappings) {
        this.mappings = mappings;
    }

    public FhirConnectModel withMappings(List<Mapping> mappings) {
        this.mappings = mappings;
        return this;
    }

    @JsonProperty("terminology")
    public Terminology getTerminology() {
        return terminology;
    }


    @JsonProperty("terminology")
    public void setTerminology(Terminology terminology) {
        this.terminology = terminology;
    }

    public FhirConnectModel withTerminology(Terminology terminology) {
        this.terminology = terminology;
        return this;
    }


    /**
     *
     * (Required)
     *
     */
    @JsonProperty("grammar")
    public String getGrammar() {
        return grammar;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("grammar")
    public void setGrammar(String grammar) {
        this.grammar = grammar;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("type")
    public SchemaType getType() {
        return type;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("type")
    public void setType(SchemaType type) {
        this.type = type;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("metadata")
    public Metadata getMetadata() {
        return metadata;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("metadata")
    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("spec")
    public Spec getSpec() {
        return spec;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("spec")
    public void setSpec(Spec spec) {
        this.spec = spec;
    }


}
