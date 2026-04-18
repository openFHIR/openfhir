package com.syntaric.openfhir.mapping.helpers;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.syntaric.openfhir.TestOpenFhirMappingContext;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.util.FhirConnectModelMerger;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import com.syntaric.openfhir.util.OpenFhirTestUtility;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.junit.Before;
import org.mockito.MockitoAnnotations;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.skyscreamer.jsonassert.JSONAssert;

public abstract class GenericHelpersTest {

    final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
    final FhirConnectModelMerger fhirConnectModelMerger = new FhirConnectModelMerger();
    protected final FhirPathR4 fhirPath = new FhirPathR4(FhirContext.forR4());

    protected TestOpenFhirMappingContext repo;
    protected HelpersCreator helpersCreator;
    protected FhirConnectContext context;
    protected OPERATIONALTEMPLATE operationaltemplate;
    protected String operationaltemplateSerialized;
    protected WebTemplate webTemplate;

    AutoCloseable closeable;

    @Before
    public void init() {
        closeable = MockitoAnnotations.openMocks(this); // Initialize mocks

        repo = new TestOpenFhirMappingContext(fhirConnectModelMerger);

        final OpenFhirMapperUtils openFhirMapperUtils = new OpenFhirMapperUtils();
        helpersCreator = new HelpersCreator(repo, new AqlToFlatPathConverter(openFhirStringUtils, openFhirMapperUtils),
                                            openFhirStringUtils);

        prepareState();
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    protected abstract void prepareState();


    protected String getFlat(final String path) {
        final InputStream inputStream = this.getClass().getResourceAsStream(path);
        try {
            return IOUtils.toString(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected FhirConnectContext getContext(final String path) {
        final ObjectMapper yaml = OpenFhirTestUtility.getYaml();
        final InputStream inputStream = this.getClass().getResourceAsStream(path);
        try {
            return yaml.readValue(inputStream, FhirConnectContext.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected OPERATIONALTEMPLATE getOperationalTemplate() {
        try {
            return TemplateDocument.Factory.parse(operationaltemplateSerialized).getTemplate();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void writeHelpersToJson(final List<MappingHelper> helpers, final String fileName) {
        try {
            final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            final File dir = new File("src/test/resources/com/syntaric/openfhir/mapping/helpers");
            dir.mkdirs();
            mapper.writeValue(new File(dir, fileName), helpers);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void assertHelpersMatchJson(final List<MappingHelper> helpers, final String resourceName) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            final String actual = mapper.writeValueAsString(helpers);

            writeHelpersToJson(helpers, resourceName);

            final InputStream is = getClass().getResourceAsStream(
                    "/com/syntaric/openfhir/mapping/helpers/" + resourceName);
            final String expected = IOUtils.toString(is, StandardCharsets.UTF_8);
            JSONAssert.assertEquals(expected, actual, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
