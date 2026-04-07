package com.syntaric.openfhir.manager;

import com.syntaric.openfhir.db.FhirConnectService;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.db.entity.FhirConnectModelEntity;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FhirConnectManager {

    private final FhirConnectService fhirConnectService;

    @Autowired
    public FhirConnectManager(final FhirConnectService fhirConnectService) {
        this.fhirConnectService = fhirConnectService;
    }

    public FhirConnectModelEntity upsertModelMapper(final FhirConnectModel fhirConnectModel, final String id,
                                                    final String reqId) {
        return fhirConnectService.upsertModelMapper(fhirConnectModel, id, reqId);
    }

    public FhirConnectContextEntity upsertContextMapper(final FhirConnectContext fhirContext, final String id,
                                                        final String reqId) {
        return fhirConnectService.upsertContextMapper(fhirContext, id, reqId);
    }

    public List<FhirConnectModel> allUserModelMappers(final String reqId) {
        return fhirConnectService.allUserModelMappers(reqId);
    }

    public FhirConnectModel readModelMappers(final String id) {
        return fhirConnectService.readModelMappers(id);
    }

    public FhirConnectContext readContextMappers(final String id) {
        return fhirConnectService.readContextMappers(id);
    }

    public List<FhirConnectContext> allUserContextMappers(final String reqId) {
        return fhirConnectService.allUserContextMappers(reqId);
    }

    public FhirConnectContextEntity findContextByTemplateId(final String templateId) {
        return fhirConnectService.findContextByTemplateId(templateId);
    }

    public List<FhirConnectContextEntity> allUserContextEntities() {
        return fhirConnectService.allUserContextEntities();
    }

    public List<FhirConnectModelEntity> findModelsByNames(final List<String> names) {
        return fhirConnectService.findModelsByNames(names);
    }

    public void deleteAllTenant() {
        fhirConnectService.deleteAllTenant();
    }
}
