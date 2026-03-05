package com.syntaric.openfhir.db.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.UuidGenerator;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity                             // for postgres
@Table(name = "fhir_connect_context")         // for postgres
public class FhirConnectContextEntity extends UserBasedEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    String id;

    @Setter(AccessLevel.NONE)  // Prevents setter generation for this field
    @org.springframework.data.annotation.Transient // so it will be ignored by mongo
    @Lob
    @JsonIgnore
    String fhirConnectContextJson;


    @Setter(AccessLevel.NONE)  // Prevents setter generation for this field
    @Getter(AccessLevel.NONE)  // Prevents getter generation for this field
    @org.springframework.data.annotation.Transient // so it will be ignored by mongo
    String templateId;

    @Transient
    FhirConnectContext fhirConnectContext;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        if (fhirConnectContext == null) {
            return;
        }
        // Serialize object to JSON before persisting
        this.fhirConnectContextJson = new Gson().toJson(fhirConnectContext);
        this.templateId = fhirConnectContext.getContext().getTemplate().getId();
    }

    @PostLoad
    public void postLoad() {
        if (StringUtils.isEmpty(fhirConnectContextJson)) {
            return;
        }
        // Deserialize JSON after loading from DB
        this.fhirConnectContext = new Gson().fromJson(fhirConnectContextJson, FhirConnectContext.class);
        this.fhirConnectContext.setId(id);
    }

    public String getTemplateId() {
        if(templateId == null && fhirConnectContext != null && fhirConnectContext.getContext() != null) {
            templateId = fhirConnectContext.getContext().getTemplate().getId();
        }
        return templateId;
    }

    public FhirConnectContext getFhirConnectContext() {
        if(fhirConnectContext != null) {
            fhirConnectContext.setId(id);
        }
        return fhirConnectContext;
    }
}
