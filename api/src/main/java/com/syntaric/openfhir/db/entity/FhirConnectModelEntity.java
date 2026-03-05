package com.syntaric.openfhir.db.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.UuidGenerator;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity                             // for postgres
@Table(name = "fhir_connect_model")         // for postgres
public class FhirConnectModelEntity extends UserBasedEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    String id;

    @Setter(AccessLevel.NONE)  // Prevents setter generation for this field
    @org.springframework.data.annotation.Transient // so it will be ignored by mongo
    @Lob
    @JsonIgnore
    String fhirConnectModelJson;

    String archetype;
    String name;

    @Transient
    FhirConnectModel fhirConnectModel;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        if (fhirConnectModel == null) {
            return;
        }
        // Serialize object to JSON before persisting
        this.fhirConnectModelJson = new Gson().toJson(fhirConnectModel);
        if (fhirConnectModel.getSpec().getOpenEhrConfig() != null) {
            this.archetype = fhirConnectModel.getSpec().getOpenEhrConfig().getArchetype();
        }
        if (fhirConnectModel.getMetadata() != null) {
            this.name = fhirConnectModel.getMetadata().getName();
        }
    }

    @PostLoad
    public void postLoad() {
        if (StringUtils.isEmpty(fhirConnectModelJson)) {
            return;
        }
        // Deserialize JSON after loading from DB
        this.fhirConnectModel = new Gson().fromJson(fhirConnectModelJson, FhirConnectModel.class);
        this.fhirConnectModel.setId(id);
    }

    public String getName() {
        if (name == null && fhirConnectModel != null) {
            name = fhirConnectModel.getMetadata().getName();
        }
        return name;
    }

    public String getArchetype() {
        if (archetype == null
                && fhirConnectModel != null
                && fhirConnectModel.getSpec() != null
                && fhirConnectModel.getSpec().getOpenEhrConfig() != null) {
            archetype = fhirConnectModel.getSpec().getOpenEhrConfig().getArchetype();
        }
        return archetype;
    }

    public FhirConnectModel getFhirConnectModel() {
        if(fhirConnectModel != null) {
            fhirConnectModel.setId(id);
        }
        return fhirConnectModel;
    }
}


