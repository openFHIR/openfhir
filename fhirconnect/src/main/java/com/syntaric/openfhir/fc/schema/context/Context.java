
package com.syntaric.openfhir.fc.schema.context;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.syntaric.openfhir.fc.schema.context.ContextProfile;
import com.syntaric.openfhir.fc.schema.context.ContextTemplate;

import java.io.Serializable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "scope",
        "profile",
        "template",
        "profileUrl",
        "templateId",
        "archetypes",
        "extensions",
        "contexts",
        "start",
        "_query"
})
public class Context implements Serializable {

    public static final String SCOPE_ROOT = "root";
    public static final String SCOPE_SUB = "sub";

    /**
     * root (default) or sub. A sub-context is only ever entered via slotContext, never picked as a root.
     */
    @JsonProperty("scope")
    private String scope;

    /**
     * (Required)
     */
    @JsonProperty("profile")
    private ContextProfile profile;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("template")
    private ContextTemplate template;
    /**
     * (Required)
     */
    @JsonProperty("archetypes")
    private List<String> archetypes;
    /**
     * (Required)
     */
    @JsonProperty("extensions")
    private List<String> extensions;
    /**
     * Names of the sub-contexts this context enters via slotContext.
     */
    @JsonProperty("contexts")
    private List<String> contexts;
    /**
     * (Required)
     */
    @JsonProperty("start")
    private String start;

    @JsonProperty("_query")
    private List<ContextQuery> query;

    /**
     * (Required)
     */
    @JsonProperty("profile")
    public ContextProfile getProfile() {
        return profile;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("profile")
    public void setProfile(ContextProfile profile) {
        this.profile = profile;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("template")
    public ContextTemplate getTemplate() {
        return template;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("template")
    public void setTemplate(ContextTemplate template) {
        this.template = template;
    }

    /**
     * (Required)
     */
    @JsonProperty("archetypes")
    public List<String> getArchetypes() {
        return archetypes;
    }

    /**
     * (Required)
     */
    @JsonProperty("archetypes")
    public void setArchetypes(List<String> archetypes) {
        this.archetypes = archetypes;
    }

    /**
     * (Required)
     */
    @JsonProperty("extensions")
    public List<String> getExtensions() {
        return extensions;
    }

    /**
     * (Required)
     */
    @JsonProperty("extensions")
    public void setExtensions(List<String> extensions) {
        this.extensions = extensions;
    }

    /**
     * (Required)
     */
    @JsonProperty("start")
    public String getStart() {
        return start;
    }

    /**
     * (Required)
     */
    @JsonProperty("start")
    public void setStart(String start) {
        this.start = start;
    }

    @JsonProperty("scope")
    public String getScope() {
        return scope;
    }

    @JsonProperty("scope")
    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * @return true if this context is a sub-context (scope: sub); scope defaults to root
     */
    @JsonIgnore
    public boolean isSubContext() {
        return SCOPE_SUB.equalsIgnoreCase(scope);
    }

    @JsonProperty("contexts")
    public List<String> getContexts() {
        return contexts;
    }

    @JsonProperty("contexts")
    public void setContexts(List<String> contexts) {
        this.contexts = contexts;
    }

    @JsonProperty("_query")
    public List<ContextQuery> getQuery() {
        return query;
    }

    @JsonProperty("_query")
    public void setQuery(List<ContextQuery> query) {
        this.query = query;
    }

}
