package com.syntaric.openfhir.manager;

import com.syntaric.openfhir.db.OptService;
import com.syntaric.openfhir.db.entity.OptEntity;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OptManager {

    private final OptService optService;

    @Autowired
    public OptManager(final OptService optService) {
        this.optService = optService;
    }

    public OptEntity upsert(final String opt, final String id, final String reqId) {
        return optService.upsert(opt, id, reqId);
    }

    public String getContent(final String id) {
        return optService.getContent(id);
    }

    public String getContentByTemplateId(final String templateId) {
        return optService.getContentByTemplateId(templateId);
    }

    public OptEntity byTemplateIdAndOrganization(final String templateId) {
        return optService.byTemplateIdAndOrganization(templateId);
    }

    public List<OptEntity> allOfUser(final String reqId) {
        return optService.allOfUser(reqId);
    }

    public void deleteAllTenant() {
        optService.deleteAllTenant();
    }
}
