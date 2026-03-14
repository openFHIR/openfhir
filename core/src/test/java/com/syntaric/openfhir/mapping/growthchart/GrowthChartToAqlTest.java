package com.syntaric.openfhir.mapping.growthchart;

import com.syntaric.openfhir.aql.ToAqlRequest;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.mapping.GenericToAqlTest;
import com.syntaric.openfhir.rest.RequestValidationException;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.junit.Assert;
import org.junit.Test;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;

import java.io.IOException;

public class GrowthChartToAqlTest extends GenericToAqlTest {

    final String MODEL_MAPPINGS = "/growth_chart/";
    final String CONTEXT_MAPPING = "/growth_chart/growth-chart.context.yml";
    final String HELPER_LOCATION = "/growth_chart/";
    final String OPT = "Growth chart.opt";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Override
    protected String templateId() {
        return "Growth chart";
    }

    @Override
    protected WebTemplate webTemplate() {
        return new OPTParser(operationaltemplate()).parse();
    }

    @Override
    protected OPERATIONALTEMPLATE operationaltemplate() {
        try {
            operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        operationaltemplate = getOperationalTemplate();
        return operationaltemplate;
    }

    @Override
    protected FhirConnectContext context() {
        return getContext(CONTEXT_MAPPING);
    }

    @Test
    public void toAql_nonExistentTemplateId() throws IOException {
        try {
            final ToAqlRequest toAqlRequest = new ToAqlRequest("random",
                    "123", "Observation?category=weight");
            ToAqlResponse aql = toAql.toAql(toAqlRequest);
        } catch (RequestValidationException e) {
            if (e.getMessage().contains("Couldn't find context relevant to template random or profile null")) {
                return;
            }
        }
        Assert.fail("Should throw RequestValidationException");
    }

    @Test
    public void toAql_nonExistentProfileUrl() throws IOException {
        try {
            final ToAqlRequest toAqlRequest = new ToAqlRequest(null,
                    "123", "Observation?category=weight&_profile=random");
            ToAqlResponse aql = toAql.toAql(toAqlRequest);
        } catch (RequestValidationException e) {
            if (e.getMessage().contains("Couldn't find context relevant to template null or profile random")) {
                return;
            }
        }
        Assert.fail("Should throw RequestValidationException");
    }

    @Test
    public void toAql_noLimitingFactor() throws IOException {
        final ToAqlRequest toAqlRequest = new ToAqlRequest(null,
                "123", "Observation?value-quantity=500&category=weight");
        final ToAqlResponse aql = toAql.toAql(toAqlRequest);
        final ToAqlResponse.AqlResponse compositionAql = aql.getAqls().stream().filter(a -> a.getType() == ToAqlResponse.AqlType.COMPOSITION)
                .findAny().orElse(null);
        final ToAqlResponse.AqlResponse entryAql = aql.getAqls().stream().filter(a -> a.getType() == ToAqlResponse.AqlType.ENTRY)
                .findAny().orElse(null);
        Assert.assertEquals("SELECT c from EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value='{{ehrid}}' and c/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]/data[at0001]/items[at0004]/value = 500", compositionAql.getAql());
        Assert.assertEquals("SELECT h FROM EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.body_weight.v2] WHERE e/ehr_id/value='{{ehrid}}' AND h/data[at0002]/events[at0003]/data[at0001]/items[at0004]/value = 500", entryAql.getAql());
    }


    @Test
    public void toAql_limitingFactorWithoutMappers() throws IOException {
        final ToAqlRequest toAqlRequest = new ToAqlRequest(null,
                "123", "Observation?value-quantity=500&category=ccdd");
        final ToAqlResponse aql = toAql.toAql(toAqlRequest);
        Assert.assertNull(aql.getAqls());
    }


}
