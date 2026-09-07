
package com.syntaric.openfhir.fc.schema.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "targetRoot",
        "targetAttributes",
        "operator",
        "criterias",
        "identifying",
})

public class Condition implements Serializable {

    /**
     * (Required)
     */
    @JsonProperty("targetRoot")
    private String targetRoot;
    /**
     * (Required)
     */
    @JsonProperty("targetAttributes")
    private List<String> targetAttributes; // if multiple, then OR is implied between them. If you want AND, you need to write multiple conditions

    /**
     * Legacy singular form. Kept (though never exposed over Jackson) because persisted documents
     * written before the plural migration still carry it: Gson (the Postgres entity round trip) and
     * Spring Data Mongo bind by field name and know nothing about the {@code @JsonSetter}
     * normalization — without this field the old value is silently dropped and
     * {@link #getTargetAttributes()} comes back {@code null}, NPE-ing helper creation.
     * Normalized into {@link #targetAttributes} lazily by the getter.
     */
    @JsonIgnore
    private String targetAttribute;

    /**
     * (Required)
     */
    @JsonProperty("operator")
    private String operator;
    /**
     * (Required)
     */
    @JsonProperty("criterias")
    private List<String> criterias;

    /** Legacy singular form; see {@link #targetAttribute} for why it must remain bindable. */
    @JsonIgnore
    private String criteria;
    @JsonProperty("identifying")
    private Boolean identifying;

    // Runtime-computed flat paths for openEHR conditions (not serialized from/to YAML)
    @Getter
    @Setter
    @JsonIgnore
    private String targetRootFlatPath;

    /**
     * Runtime-only (not serialized). Non-null when this FHIR condition's predicate applies to the
     * results of the mapped fhir path itself rather than to elements at the condition's
     * targetRoot; the value is the path prefix to prepend to each targetAttribute (empty string
     * for none). This replicates the placement dispatch of the legacy condition-in-fhirPath
     * string splicing, where such conditions had their where() clause appended to the end of the
     * mapped path — including the degenerate shapes whose prefixed attribute path never resolves.
     * Computed while amending conditions during helper creation.
     */
    @Getter
    @Setter
    @JsonIgnore
    private String mappedPathEndAttributePrefix;

    @Setter
    @JsonIgnore
    private List<String> targetAttributesFlatPath;

    public List<String> getTargetAttributesFlatPath() {
        if(targetAttributesFlatPath == null) {
            this.targetAttributesFlatPath = new ArrayList<>();
        }
        return targetAttributesFlatPath;
    }

    public void setTargetAttributesFlatPath(final List<String> targetAttributesFlatPath) {
        this.targetAttributesFlatPath = targetAttributesFlatPath;
    }

    public Condition copy() {
        final Condition condition = new Condition();
        condition.setTargetRoot(targetRoot);
        condition.setTargetAttributes(getTargetAttributes()); // via the getter: normalizes legacy singular
        condition.setTargetRootFlatPath(targetRootFlatPath);
        condition.setTargetAttributesFlatPath(targetAttributesFlatPath);
        condition.setOperator(operator);
        condition.setCriterias(getCriterias()); // via the getter: normalizes legacy singular
        condition.setIdentifying(identifying == null ? null : new Boolean(identifying.booleanValue()));
        condition.setMappedPathEndAttributePrefix(mappedPathEndAttributePrefix);
        return condition;
    }

    /**
     * (Required)
     */
    @JsonProperty("targetRoot")
    public String getTargetRoot() {
        return targetRoot;
    }

    /**
     * (Required)
     */
    @JsonProperty("targetRoot")
    public void setTargetRoot(String targetRoot) {
        this.targetRoot = targetRoot;
    }

    public Condition withTargetRoot(String targetRoot) {
        this.targetRoot = targetRoot;
        return this;
    }

    /**
     * (Required)
     * <p>
     * Falls back to the legacy singular {@code targetAttribute} when the plural list is absent, so
     * documents persisted before the plural migration (materialized by Gson or Spring Data, which
     * bypass the Jackson-level normalization) keep their condition semantics instead of surfacing a
     * {@code null} list.
     */
    @JsonProperty("targetAttributes")
    public List<String> getTargetAttributes() {
        if ((targetAttributes == null || targetAttributes.isEmpty()) && targetAttribute != null) {
            targetAttributes = new ArrayList<>(List.of(targetAttribute));
        }
        return targetAttributes;
    }

    /**
     * (Required)
     */
    @JsonProperty("targetAttributes")
    public void setTargetAttributes(List<String> targetAttributes) {
        this.targetAttributes = targetAttributes;
    }

    public Condition withTargetAttributes(List<String> targetAttributes) {
        this.targetAttributes = targetAttributes;
        return this;
    }

    /**
     * Backward compatibility for mappings still using the deprecated singular {@code targetAttribute}
     * key: it is normalized into {@code targetAttributes} at deserialization time.
     */
    @JsonSetter("targetAttribute")
    private void setSingularTargetAttribute(String targetAttribute) {
        this.targetAttributes = new ArrayList<>(List.of(targetAttribute));
    }

    /**
     * (Required)
     */
    @JsonProperty("operator")
    public String getOperator() {
        return operator;
    }

    /**
     * (Required)
     */
    @JsonProperty("operator")
    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Condition withOperator(String operator) {
        this.operator = operator;
        return this;
    }

    /**
     * Backward compatibility for mappings still using the deprecated singular {@code criteria}
     * key: it is normalized into {@code criterias} at deserialization time.
     */
    @JsonSetter("criteria")
    private void setSingularCriteria(String criteria) {
        this.criterias = new ArrayList<>(List.of(criteria));
    }

    /**
     * Falls back to the legacy singular {@code criteria} when the plural list is absent; see
     * {@link #getTargetAttributes()}.
     */
    @JsonProperty("criterias")
    public List<String> getCriterias() {
        if ((criterias == null || criterias.isEmpty()) && criteria != null) {
            criterias = new ArrayList<>(List.of(criteria));
        }
        return criterias;
    }

    @JsonProperty("criterias")
    public void setCriterias(List<String> criterias) {
        this.criterias = criterias;
    }

    public Condition withCriterias(String criteria) {
        if(criterias == null) {
            this.criterias = new ArrayList<>();
        }
        this.criterias.add(criteria);
        return this;
    }



    @JsonProperty("identifying")
    public Boolean getIdentifying() {
        return identifying;
    }

    @JsonProperty("identifying")
    public void setIdentifying(Boolean identifying) {
        this.identifying = identifying;
    }

    public Condition withIdentifying(Boolean identifying) {
        this.identifying = identifying;
        return this;
    }

}
