package com.syntaric.openfhir.db.repository.mongodb;

import com.syntaric.openfhir.db.repository.FhirConnectModelRepository;
import com.syntaric.openfhir.db.entity.FhirConnectModelEntity;
import java.util.List;
import lombok.NonNull;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface FhirConnectModelMongoRepository extends FhirConnectModelRepository, MongoRepository<FhirConnectModelEntity, String> {
    @Query("{'organisation': ?0}")
    List<FhirConnectModelEntity> findByTenant(@NonNull final String organisation);
    @Query("{'id': ?1, 'organisation': ?0}")
    FhirConnectModelEntity readByTenant(@NonNull final String organisation, @NonNull final String id);

    @Query("{'fhirConnectModel.openEhrConfig.archetype': { $in: ?0 }, 'organisation': ?1}")
    List<FhirConnectModelEntity> findByTenantAndArchetype(final List<String> archetype, @NonNull final String organisation);

    @Query("{'fhirConnectModel.metadata.name': { $in: ?0 }, 'organisation': ?1}")
    List<FhirConnectModelEntity> findByTenantAndName(final List<String> name, @NonNull final String organization);

    @Query(value = "{'organisation': ?0}", delete = true)
    void deleteAllTenant(@NonNull String organisation);
}
