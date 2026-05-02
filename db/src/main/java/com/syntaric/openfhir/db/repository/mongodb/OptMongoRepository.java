package com.syntaric.openfhir.db.repository.mongodb;

import com.syntaric.openfhir.db.repository.OptRepository;
import com.syntaric.openfhir.db.entity.OptEntity;
import java.util.List;
import lombok.NonNull;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;


public interface OptMongoRepository extends OptRepository, MongoRepository<OptEntity, String> {

    @Query(fields = "{ 'content' : 0 }")
    List<OptEntity> findByOrganisation(@NonNull final String organisation);
    @Query(value = "{'id': ?1, 'organisation': ?0}", fields = "{ 'content' : 0 }")
    String readByOrganisation(@NonNull final String organisation, @NonNull final String id);

    OptEntity findByTemplateIdAndOrganisation(final String templateId, @NonNull final String organisation);

    @Query(value = "{'id': ?0, 'organisation': ?1}")
    OptEntity findByIdAndOrganisation(final String id, @NonNull final String organisation);

    @Query(value = "{'organisation': ?0}", delete = true)
    void deleteAllTenant(@NonNull String organisation);

    @Query(value = "{'id': ?0, 'organisation': ?1}", delete = true)
    void deleteByIdAndOrganisation(@NonNull String id, @NonNull String organisation);
}
