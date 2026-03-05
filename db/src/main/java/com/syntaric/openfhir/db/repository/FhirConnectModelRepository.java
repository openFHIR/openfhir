package com.syntaric.openfhir.db.repository;

import com.syntaric.openfhir.db.entity.FhirConnectModelEntity;
import java.util.List;

public interface FhirConnectModelRepository {
    List<FhirConnectModelEntity> findByTenant(final String user);
    FhirConnectModelEntity readByTenant(final String user, final String id);

    List<FhirConnectModelEntity> findByTenantAndArchetype(final List<String> archetype, final String user);

    List<FhirConnectModelEntity> findByTenantAndName(final List<String> name, final String user);
    FhirConnectModelEntity save(final FhirConnectModelEntity entity);

    void deleteAll();
    void deleteAllTenant(final String user);
}
