package com.syntaric.openfhir.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@MappedSuperclass
public abstract class UserBasedEntity implements Serializable {
    @Column(name = "\"user\"") // because user is a reserved word
    private String user;
    private String organisation;
    Date created;
    Date updated;
}
