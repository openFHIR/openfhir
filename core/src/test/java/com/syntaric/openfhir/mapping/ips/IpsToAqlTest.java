package com.syntaric.openfhir.mapping.ips;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntaric.openfhir.OpenFhirMappingContext;
import com.syntaric.openfhir.TestOpenFhirMappingContext;
import com.syntaric.openfhir.aql.ToAqlRequest;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.manager.FhirConnectManager;
import com.syntaric.openfhir.manager.OptManager;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.db.entity.OptEntity;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.mapping.helpers.AqlToFlatPathConverter;
import com.syntaric.openfhir.mapping.helpers.HelpersCreator;
import com.syntaric.openfhir.mapping.toaql.AqlPatientScope;
import com.syntaric.openfhir.mapping.toaql.OpenEhrAqlPopulator;
import com.syntaric.openfhir.mapping.toaql.ToAql;
import com.syntaric.openfhir.mapping.toaql.ToAqlMappingEngine;
import com.syntaric.openfhir.mapping.toaql.FederationGatewaySubjectPredicateRule;
import com.syntaric.openfhir.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

@Slf4j
public class IpsToAqlTest {
    AutoCloseable closeable;

    @Mock
    protected FhirConnectManager fhirConnectManager;

    @Mock
    protected OptManager optManager;

    @Mock
    protected OpenEhrTemplateUtils openEhrTemplateUtils;

    final String DIR = getClass().getResource("/ips/").getFile();
    final String CONTEXT_DIR = getClass().getResource("/ips/").getFile();

    private ToAql toAql;

    @Before
    public void setupState() {
        closeable = MockitoAnnotations.openMocks(this); // Initialize mocks

        final TestOpenFhirMappingContext repo = new TestOpenFhirMappingContext(new FhirConnectModelMerger());

        final List<FhirConnectContextEntity> allContextEntities = contextFiles().stream().map(x -> {
            FhirConnectContextEntity fhirConnectContextEntity = new FhirConnectContextEntity();
            fhirConnectContextEntity.setFhirConnectContext(parseContext(x));
            return fhirConnectContextEntity;
        }).toList();
        doReturn(allContextEntities).when(fhirConnectManager).allUserContextEntities();

        for (String contextFile : contextFiles()) {
            try {
                final FhirConnectContext context = parseContext(contextFile);
                final OPERATIONALTEMPLATE operationalTemplate = getOperationalTemplate();
                repo.initRepository(context, operationalTemplate, DIR);

                final FhirConnectContextEntity toBeReturned = new FhirConnectContextEntity();
                toBeReturned.setFhirConnectContext(context);

                final String templateId = operationalTemplate.getTemplateId().getValue();
                final String normalizedTemplateId = OpenFhirMappingContext.normalizeTemplateId(templateId);
                doReturn(toBeReturned).when(fhirConnectManager).findContextByTemplateId(eq(normalizedTemplateId));

                final OptEntity optEntity = new OptEntity();
                optEntity.setContent(getOperationalTemplateContent());
                doReturn(new OPTParser(operationalTemplate).parse()).when(openEhrTemplateUtils).parseWebTemplate(eq(optEntity));
                doReturn(optEntity).when(optManager).byTemplateIdAndOrganization(eq(normalizedTemplateId));

            } catch (Exception e) {
                log.error("{}", e.getMessage());
            }
        }

        final HelpersCreator helpersCreator1 = new HelpersCreator(repo, new AqlToFlatPathConverter(
                new OpenFhirStringUtils()));
        toAql = new ToAql(fhirConnectManager, new OpenFhirMapperUtils(), repo, new ToAqlMappingEngine(new OpenEhrAqlPopulator()), helpersCreator1,
                openEhrTemplateUtils, null, optManager);
    }

    @Test
    public void limitByResource_archetypeonly() {
        final ToAqlRequest toAqlRequest = new ToAqlRequest(null,
                "123", "Condition?verification-status=confirmed");
        final ToAqlResponse aql = toAql.toAql(toAqlRequest);
        final ToAqlResponse.AqlResponse compositionAql = aql.getAqls().stream().filter(a -> a.getType() == ToAqlResponse.AqlType.COMPOSITION)
                .findAny().orElse(null);
        final ToAqlResponse.AqlResponse entryAql = aql.getAqls().stream().filter(a -> a.getType() == ToAqlResponse.AqlType.ENTRY)
                .findAny().orElse(null);
    }

    private static final String SUBJECT_ID = "194303193454";
    private static final String SUBJECT_NAMESPACE = "1.2.752.129.2.1.3.1";

    @Test
    public void bySubject_conditionVerificationStatusConfirmed() {
        final ToAqlResponse response = toAqlBySubject("Condition?verification-status=confirmed", SUBJECT_ID,
                                                      SUBJECT_NAMESPACE);
        final ToAqlResponse.AqlResponse entryAql = response.getAqls().stream()
                .filter(a -> a.getType() == ToAqlResponse.AqlType.ENTRY)
                .findAny().orElseThrow();
        Assert.assertTrue(entryAql.getAql(), entryAql.getAql().startsWith(
                "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.problem_diagnosis.v1] WHERE e/ehr_status/subject/external_ref/id/value = '194303193454' AND e/ehr_status/subject/external_ref/namespace = '1.2.752.129.2.1.3.1'"));
        FederationGatewaySubjectPredicateRule.assertAllScopedBySubject(response, SUBJECT_ID, SUBJECT_NAMESPACE);
    }

    /** Every AQL of every IPS section is scoped by the subject in the form Unio's SubjectPredicateExtractor accepts. */
    @Test
    public void bySubject_everyIpsSection_isAcceptedByUnio() {
        for (final String query : List.of("AllergyIntolerance", "Condition?verification-status=confirmed",
                                          "DeviceUseStatement")) {
            for (final String namespace : new String[]{null, SUBJECT_NAMESPACE}) {
                final ToAqlResponse response = toAqlBySubject(query, SUBJECT_ID, namespace);
                Assert.assertTrue(query, response.getAqls() != null && !response.getAqls().isEmpty());
                FederationGatewaySubjectPredicateRule.assertAllScopedBySubject(response, SUBJECT_ID, namespace);
            }
        }
        FederationGatewaySubjectPredicateRule.assertAllScopedBySubject(toAqlBySubject("AllergyIntolerance", "it's-1", "urn:'x'"),
                                                                       "it's-1", "urn:'x'");
    }

    /** What OpenFhirEngine.toAql does: the request is checked, the AQLs generated, then scoped to the patient. */
    private ToAqlResponse toAqlBySubject(final String fhirFullUrl, final String id, final String namespace) {
        final ToAqlRequest request = new ToAqlRequest(null, null, fhirFullUrl, new ToAqlRequest.Subject(id, namespace));
        AqlPatientScope.validate(request);
        final ToAqlResponse response = toAql.toAql(request);
        AqlPatientScope.apply(request, response);
        return response;
    }

    private OPERATIONALTEMPLATE getOperationalTemplate() {
        try {
            return TemplateDocument.Factory.parse(getOperationalTemplateContent()).getTemplate();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getOperationalTemplateContent() {
        final File optDir = new File(DIR, "");
        final File[] optFiles = optDir.listFiles(f -> f.getName().endsWith(".opt"));
        if (optFiles == null || optFiles.length == 0) {
            throw new RuntimeException("No .opt file found in " + optDir.getAbsolutePath());
        }
        try {
            return FileUtils.readFileToString(optFiles[0]);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private FhirConnectContext parseContext(String path) {
        final ObjectMapper yaml = OpenFhirTestUtility.getYaml();
        try {
            return yaml.readValue(getFileContent(path), FhirConnectContext.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    private String getFileContent(final String filePath) {
        return FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8);
    }

    private List<String> contextFiles() {
        final List<String> result = new ArrayList<>();
        collectContextFiles(new File(CONTEXT_DIR), result);
        return result;
    }

    private void collectContextFiles(final File dir, final List<String> result) {
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (final File file : files) {
            if (file.isDirectory()) {
                collectContextFiles(file, result);
            } else if (file.getName().endsWith("context.yml")) {
                result.add(file.getAbsolutePath());
            }
        }
    }
}
