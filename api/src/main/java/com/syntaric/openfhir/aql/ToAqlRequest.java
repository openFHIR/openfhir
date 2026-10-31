package com.syntaric.openfhir.aql;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * A FHIR search to translate to AQL. The patient is named by {@code ehrId} or by {@code subject}, not both; with
 * neither, the AQLs keep the {@code {{ehrid}}} placeholder for the caller to fill.
 */
@Data
public class ToAqlRequest {
    private final String template;
    /** The patient's EHR: the AQLs are scoped by {@code e/ehr_id/value}. */
    private final String ehrId;
    private final String fhirFullUrl;
    /**
     * The patient as the {@code EHR_STATUS.subject} of their EHR: the AQLs are scoped by
     * {@code e/ehr_status/subject/external_ref/id/value} (and {@code .../namespace}), which also holds where one
     * patient has a different EHR in each repository, as behind a federation gateway.
     */
    private final Subject subject;

    public ToAqlRequest(final String template, final String ehrId, final String fhirFullUrl) {
        this(template, ehrId, fhirFullUrl, null);
    }

    @JsonCreator
    public ToAqlRequest(@JsonProperty("template") final String template,
                        @JsonProperty("ehrId") final String ehrId,
                        @JsonProperty("fhirFullUrl") final String fhirFullUrl,
                        @JsonProperty("subject") final Subject subject) {
        this.template = template;
        this.ehrId = ehrId;
        this.fhirFullUrl = fhirFullUrl;
        this.subject = subject;
    }

    /**
     * The {@code PARTY_REF} of an {@code EHR_STATUS.subject}: its {@code id/value} and optional {@code namespace}.
     * They identify a person (typically by a national identifier), so {@link #toString()} leaves them out: the
     * request is logged.
     */
    @Data
    public static class Subject {
        private final String id;
        private final String namespace;

        @JsonCreator
        public Subject(@JsonProperty("id") final String id,
                       @JsonProperty("namespace") final String namespace) {
            this.id = id;
            this.namespace = namespace;
        }

        @Override
        public String toString() {
            return "Subject(id=" + (id == null ? "null" : "<redacted>")
                    + ", namespace=" + (namespace == null ? "null" : "<redacted>") + ")";
        }
    }
}
