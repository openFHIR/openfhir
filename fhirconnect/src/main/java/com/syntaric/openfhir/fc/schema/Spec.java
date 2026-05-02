
package com.syntaric.openfhir.fc.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import com.syntaric.openfhir.fc.schema.model.FhirConfig;
import com.syntaric.openfhir.fc.schema.model.OpenEhrConfig;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "system",
    "version",
    "extends",
    "openEhrConfig",
    "fhirConfig"
})
public class Spec implements Serializable {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("system")
    private System system;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    private Version version;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("extends")
    private String _extends;

    /**
     * FHIR Config
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("fhirConfig")
    private FhirConfig fhirConfig;
    /**
     * openEHR Config
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("openEhrConfig")
    private OpenEhrConfig openEhrConfig;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("system")
    public System getSystem() {
        return system;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("system")
    public void setSystem(System system) {
        this.system = system;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("extends")
    public String get_extends() {
        return _extends;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("extends")
    public void set_extends(String _extends) {
        this._extends = _extends;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public Version getVersion() {
        return version;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public void setVersion(Version version) {
        this.version = version;
    }

    @JsonProperty("fhirConfig")
    public FhirConfig getFhirConfig() {
        return fhirConfig;
    }

    @JsonProperty("fhirConfig")
    public void setFhirConfig(final FhirConfig fhirConfig) {
        this.fhirConfig = fhirConfig;
    }

    @JsonProperty("openEhrConfig")
    public OpenEhrConfig getOpenEhrConfig() {
        return openEhrConfig;
    }

    @JsonProperty("openEhrConfig")
    public void setOpenEhrConfig(final OpenEhrConfig openEhrConfig) {
        this.openEhrConfig = openEhrConfig;
    }

    public enum System {

        FHIR("FHIR");
        private final String value;
        private final static Map<String, System> CONSTANTS = new HashMap<String, System>();

        static {
            for (System c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        System(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static System fromValue(String value) {
            System constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

    
    public enum Version {

        STU3("STU3", "org.hl7.fhir.dstu3.model."),
        R4("R4", "org.hl7.fhir.r4.model."),
        R4B("R4B", "org.hl7.fhir.r4b.model."),
        R5("R5", "org.hl7.fhir.r5.model.");

        private final String value;
        private final String modelPackage;
        private final static Map<String, Version> CONSTANTS = new HashMap<String, Version>();

        static {
            for (Version c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Version(String value, String modelPackage) {
            this.value = value;
            this.modelPackage = modelPackage;
        }

        public String modelPackage() {
            return modelPackage;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static Version fromValue(String value) {
            Version constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }




    public enum Extension {

    }

}
