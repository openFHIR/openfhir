package com.syntaric.openfhir.db.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity                             // for postgres
@Table(name = "bootstrap")         // for postgres
public class BootstrapEntity extends UserBasedEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    String id;
    String file;
    Date date;
    String path;         // relative to the bootstrap dir, e.g. "kds/diagnose.context.yaml"
    String contentHash;  // SHA-256 hex of the file content at the time it was bootstrapped
    String entityId;     // engine-assigned id of the created model mapper / context mapper / OPT
    String entityType;   // MODEL | CONTEXT | OPT

    public BootstrapEntity(final String id, final String file, final Date date) {
        this(id, file, date, null);
    }

    /**
     * {@code organisation} lives on {@link UserBasedEntity}, so Lombok's generated constructor and builder do not
     * cover it — it has to be set afterwards, the same way {@code OptEntity.copy()} does.
     */
    public BootstrapEntity(final String id, final String file, final Date date, final String organisation) {
        this(id, file, date, null, null, null, null);
        setOrganisation(organisation);
    }
}
