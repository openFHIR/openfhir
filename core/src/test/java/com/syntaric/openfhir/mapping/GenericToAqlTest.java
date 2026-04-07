package com.syntaric.openfhir.mapping;

import com.syntaric.openfhir.manager.FhirConnectManager;
import com.syntaric.openfhir.manager.OptManager;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.db.entity.OptEntity;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.mapping.helpers.AqlToFlatPathConverter;
import com.syntaric.openfhir.mapping.helpers.HelpersCreator;
import com.syntaric.openfhir.mapping.toaql.OpenEhrAqlPopulator;
import com.syntaric.openfhir.mapping.toaql.ToAql;
import com.syntaric.openfhir.mapping.toaql.ToAqlMappingEngine;
import com.syntaric.openfhir.util.OpenEhrTemplateUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.junit.Before;
import org.mockito.Mock;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

public abstract class GenericToAqlTest extends GenericTest {

    @Mock
    protected FhirConnectManager fhirConnectManager;

    @Mock
    protected OptManager optManager;

    @Override
    protected void customMocks() {
        final FhirConnectContextEntity toBeReturned = new FhirConnectContextEntity();
        toBeReturned.setFhirConnectContext(context());

        doReturn(List.of(toBeReturned)).when(fhirConnectManager).allUserContextEntities();
        doReturn(toBeReturned).when(fhirConnectManager).findContextByTemplateId(eq(templateId()));

        OptEntity optEntity = new OptEntity();
        optEntity.setContent(operationaltemplateContent());
        doReturn(optEntity).when(optManager).byTemplateIdAndOrganization(any());

    }

    @Before
    public void prepare() {
        final HelpersCreator helpersCreator1 = new HelpersCreator(repo, new AqlToFlatPathConverter(
                openFhirStringUtils,
                openFhirMapperUtils), openFhirStringUtils);
        toAql = new ToAql(fhirConnectManager, openFhirMapperUtils, repo, new ToAqlMappingEngine(new OpenEhrAqlPopulator()), helpersCreator1,
                new OpenEhrTemplateUtils(), null, optManager);
    }

    protected abstract WebTemplate webTemplate();

    protected abstract OPERATIONALTEMPLATE operationaltemplate();

    protected abstract String operationaltemplateContent();

    protected abstract String templateId();

    protected abstract FhirConnectContext context();
}

