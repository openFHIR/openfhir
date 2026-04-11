package com.syntaric.openfhir.db.repository;

import com.syntaric.openfhir.db.entity.OptEntity;
import java.util.List;

public interface OptRepository {
    List<OptEntity> findByOrganisation(final String user);

    OptEntity findByTemplateIdAndOrganisation(final String templateId, final String user);
    OptEntity findByIdAndOrganisation(final String id, final String org);

    String readByOrganisation(final String user, final String id);

    OptEntity save(OptEntity entity);

    void deleteAll();
    void deleteAllTenant(final String user);
    void deleteByIdAndOrganisation(final String id, final String user);
}
