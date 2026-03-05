package com.syntaric.openfhir.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptEntity extends UserBasedEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    String id;
    @Lob
    String content;

    String templateId;
    String originalTemplateId;
    String displayTemplateId;

    public OptEntity copy() {
        final OptEntity optEntity = new OptEntity(id, content, templateId, originalTemplateId, displayTemplateId);
        optEntity.setUser(getUser());
        optEntity.setOrganisation(getOrganisation());
        return optEntity;
    }
}
