package com.syntaric.openfhir.db.repository.mongodb;

import com.syntaric.openfhir.db.repository.BootstrapRepository;
import com.syntaric.openfhir.db.entity.BootstrapEntity;
import java.util.List;
import lombok.NonNull;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface BootstrapMongoRepository extends BootstrapRepository, MongoRepository<BootstrapEntity, String> {
    List<BootstrapEntity> findByFile(final String file);

    @Query("{'file': ?0, 'organisation': ?1}")
    List<BootstrapEntity> findByFileAndTenant(final String file, @NonNull final String organisation);

    @Query("{'path': ?0, 'organisation': ?1}")
    List<BootstrapEntity> findByPathAndTenant(final String path, @NonNull final String organisation);

    @Query("{'organisation': ?0}")
    List<BootstrapEntity> findAllByTenant(@NonNull final String organisation);

    @Query(value = "{'organisation': ?0}", delete = true)
    void deleteAllTenant(@NonNull final String organisation);
}
