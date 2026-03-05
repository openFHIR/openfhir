package com.syntaric.openfhir.db.repository;

import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import java.util.List;

public interface FhirConnectContextRepository {
    FhirConnectContextEntity findByTemplateIdAndTenant(final String templateId, final String user);

    FhirConnectContextEntity readByTenant(final String user, final String id);

    List<FhirConnectContextEntity> findByTenant(final String user);

    FhirConnectContextEntity save(final FhirConnectContextEntity entity);

    void deleteAll();
    void deleteAllTenant(final String user);
}
