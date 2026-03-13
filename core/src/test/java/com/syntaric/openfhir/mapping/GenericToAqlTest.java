package com.syntaric.openfhir.mapping;

import com.syntaric.openfhir.OpenFhirMappingContext;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.db.repository.FhirConnectContextRepository;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.mapping.helpers.AqlToFlatPathConverter;
import com.syntaric.openfhir.mapping.helpers.HelpersCreator;
import com.syntaric.openfhir.mapping.toaql.OpenEhrAqlPopulator;
import com.syntaric.openfhir.mapping.toaql.ToAql;
import com.syntaric.openfhir.mapping.toaql.ToAqlMappingEngine;
import com.syntaric.openfhir.producers.NoOpUserContextProducer;
import com.syntaric.openfhir.util.OpenEhrCachedUtils;
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
    protected FhirConnectContextRepository contextRepository;

    @Mock
    protected OpenEhrCachedUtils openEhrCachedUtils;

    @Override
    protected void customMocks() {
        final FhirConnectContextEntity toBeReturned = new FhirConnectContextEntity();
        toBeReturned.setFhirConnectContext(context());

        doReturn(List.of(toBeReturned)).when(contextRepository).findByTenant(any());
        doReturn(toBeReturned).when(contextRepository).findByTemplateIdAndTenant(eq(templateId()), any());
        doReturn(webTemplate()).when(openEhrCachedUtils).parseWebTemplate(any());
        doReturn(operationaltemplate()).when(openEhrCachedUtils).getOperationalTemplate(any(), eq(OpenFhirMappingContext.normalizeTemplateId(templateId())));

    }

    @Before
    public void prepare() {
        final HelpersCreator helpersCreator1 = new HelpersCreator(repo, new AqlToFlatPathConverter(
                openFhirStringUtils,
                openFhirMapperUtils), openFhirStringUtils);
        toAql = new ToAql(contextRepository, openFhirMapperUtils, new NoOpUserContextProducer(), repo, new ToAqlMappingEngine(new OpenEhrAqlPopulator()), helpersCreator1,
                openEhrCachedUtils);
    }

    protected abstract WebTemplate webTemplate();

    protected abstract OPERATIONALTEMPLATE operationaltemplate();

    protected abstract String templateId();

    protected abstract FhirConnectContext context();
}

