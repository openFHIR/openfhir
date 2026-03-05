package com.syntaric.openfhir.db.repository.mongodb;

import com.syntaric.openfhir.db.repository.FhirConnectContextRepository;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import java.util.List;
import lombok.NonNull;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface FhirConnectContextMongoRepository extends FhirConnectContextRepository, MongoRepository<FhirConnectContextEntity, String> {

    @Query("{'fhirConnectContext.context.template.id': ?0, 'organisation': ?1}")
    FhirConnectContextEntity findByTemplateIdAndTenant(final String templateId, @NonNull final String tenant);

    @Query("{'id': ?1, 'organisation': ?0}")
    FhirConnectContextEntity readByTenant(@NonNull final String tenant, @NonNull final String id);

    @Query("{'organisation': ?0}")
    List<FhirConnectContextEntity> findByTenant(@NonNull final String tenant);

    @Query(value = "{'organisation': ?0}", delete = true)
    void deleteAllTenant(@NonNull String tenant);
}
