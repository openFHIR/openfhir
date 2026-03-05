package com.syntaric.openfhir.db.repository;

import com.syntaric.openfhir.db.entity.BootstrapEntity;
import java.util.List;

public interface BootstrapRepository {
    List<BootstrapEntity> findByFile(final String file);

    BootstrapEntity save(final BootstrapEntity entity);
}
