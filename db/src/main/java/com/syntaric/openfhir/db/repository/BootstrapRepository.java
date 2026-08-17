package com.syntaric.openfhir.db.repository;

import com.syntaric.openfhir.db.entity.BootstrapEntity;
import java.util.List;

public interface BootstrapRepository {
    List<BootstrapEntity> findByFile(final String file);

    List<BootstrapEntity> findByFileAndTenant(final String file, final String organisation);

    List<BootstrapEntity> findByPathAndTenant(final String path, final String organisation);

    List<BootstrapEntity> findAllByTenant(final String organisation);

    BootstrapEntity save(final BootstrapEntity entity);

    void deleteAll();

    void deleteAllTenant(final String organisation);
}
