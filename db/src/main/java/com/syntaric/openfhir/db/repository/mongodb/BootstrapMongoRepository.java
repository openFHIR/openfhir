package com.syntaric.openfhir.db.repository.mongodb;

import com.syntaric.openfhir.db.repository.BootstrapRepository;
import com.syntaric.openfhir.db.entity.BootstrapEntity;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BootstrapMongoRepository extends BootstrapRepository, MongoRepository<BootstrapEntity, String> {
    List<BootstrapEntity> findByFile(final String file);
}
