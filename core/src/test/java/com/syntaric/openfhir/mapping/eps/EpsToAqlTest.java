package com.syntaric.openfhir.mapping.eps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntaric.openfhir.OpenFhirMappingContext;
import com.syntaric.openfhir.TestOpenFhirMappingContext;
import com.syntaric.openfhir.aql.ToAqlRequest;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.aql.ToAqlResponse.AqlResponse;
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
import java.util.Set;
import java.util.TreeSet;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

@Slf4j
public class EpsToAqlTest {
    AutoCloseable closeable;

    @Mock
    protected FhirConnectManager fhirConnectManager;

    @Mock
    protected OptManager optManager;

    @Mock
    protected OpenEhrTemplateUtils openEhrTemplateUtils;

    final String DIR = getClass().getResource("/eps/").getFile();
    final String CONTEXT_DIR = getClass().getResource("/eps/").getFile();

    private ToAql toAql;

    @Before
    public void setupState() {
        closeable = MockitoAnnotations.openMocks(this);

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
    public void toAql_allergyIntolerance() {
        final ToAqlResponse response = toAql.toAql(new ToAqlRequest(null, "123", "AllergyIntolerance"));
        assertAql(response, ToAqlResponse.AqlType.ENTRY,
                "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.adverse_reaction_risk.v2] WHERE e/ehr_id/value='{{ehrid}}'");
        assertAql(response, ToAqlResponse.AqlType.COMPOSITION,
                "SELECT c FROM EHR e CONTAINS COMPOSITION c CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.adverse_reaction_risk.v2] WHERE e/ehr_id/value='{{ehrid}}'");
    }

    @Test
    public void toAql_conditionVerificationStatusConfirmed() {
        final ToAqlResponse response = toAql.toAql(new ToAqlRequest(null, "123", "Condition?verification-status=confirmed"));
        assertAql(response, ToAqlResponse.AqlType.ENTRY,
                "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.problem_diagnosis.v1] WHERE e/ehr_id/value='{{ehrid}}' AND h/data[at0001]/items[at0073]/value/defining_code/terminology_id/value = 'local' AND h/data[at0001]/items[at0073]/value/defining_code/code_string = 'at0076' AND h/data[at0001]/items[at0073]/value/value = 'Confirmed'");
        assertAql(response, ToAqlResponse.AqlType.COMPOSITION,
                "SELECT c from EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value='{{ehrid}}' and c/content[openEHR-EHR-SECTION.adhoc.v1 and name/value='EPS Problems']/items[openEHR-EHR-EVALUATION.problem_diagnosis.v1]/data[at0001]/items[at0073]/value/defining_code/terminology_id/value = 'local' AND c/content[openEHR-EHR-SECTION.adhoc.v1 and name/value='EPS Problems']/items[openEHR-EHR-EVALUATION.problem_diagnosis.v1]/data[at0001]/items[at0073]/value/defining_code/code_string = 'at0076' AND c/content[openEHR-EHR-SECTION.adhoc.v1 and name/value='EPS Problems']/items[openEHR-EHR-EVALUATION.problem_diagnosis.v1]/data[at0001]/items[at0073]/value/value = 'Confirmed'");
    }

    @Test
    public void toAql_procedure() {
        final ToAqlResponse response = toAql.toAql(new ToAqlRequest(null, "123", "Procedure"));
        assertAql(response, ToAqlResponse.AqlType.ENTRY,
                "SELECT h FROM EHR e CONTAINS ACTION h [openEHR-EHR-ACTION.procedure.v1] WHERE e/ehr_id/value='{{ehrid}}'");
        assertAql(response, ToAqlResponse.AqlType.COMPOSITION,
                "SELECT c FROM EHR e CONTAINS COMPOSITION c CONTAINS ACTION h [openEHR-EHR-ACTION.procedure.v1] WHERE e/ehr_id/value='{{ehrid}}'");
    }

    @Test
    public void toAql_deviceUseStatement() {
        final ToAqlResponse response = toAql.toAql(new ToAqlRequest(null, "123", "DeviceUseStatement?_include=DeviceUseStatement:device"));
        assertAql(response, ToAqlResponse.AqlType.ENTRY,
                "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.device_summary.v0] WHERE e/ehr_id/value='{{ehrid}}'");
        assertAql(response, ToAqlResponse.AqlType.COMPOSITION,
                "SELECT c FROM EHR e CONTAINS COMPOSITION c CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.device_summary.v0] WHERE e/ehr_id/value='{{ehrid}}'");
    }

    @Test
    public void toAql_byCodedText() {
        final ToAqlResponse response = toAql.toAql(new ToAqlRequest(null, "123", "AllergyIntolerance?manifestation=4386001"));
        assertAql(response, ToAqlResponse.AqlType.ENTRY,
                "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.adverse_reaction_risk.v2] WHERE e/ehr_id/value='{{ehrid}}' AND h/data[at0001]/items[openEHR-EHR-CLUSTER.adverse_reaction_event.v1]/items[at0006]/value/defining_code/code_string = '4386001'");
        assertAql(response, ToAqlResponse.AqlType.COMPOSITION,
                "SELECT c from EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value='{{ehrid}}' and c/content[openEHR-EHR-SECTION.adhoc.v1 and name/value='EPS Allergies']/items[openEHR-EHR-EVALUATION.adverse_reaction_risk.v2]/data[at0001]/items[openEHR-EHR-CLUSTER.adverse_reaction_event.v1]/items[at0006]/value/defining_code/code_string = '4386001'");
    }

    private static final String SUBJECT_ID = "194303193454";
    private static final String SUBJECT_NAMESPACE = "1.2.752.129.2.1.3.1";

    @Test
    public void toAql_bySubject_allergyIntolerance() {
        final ToAqlResponse response = toAqlBySubject("AllergyIntolerance", SUBJECT_ID, SUBJECT_NAMESPACE);
        assertAql(response, ToAqlResponse.AqlType.ENTRY,
                "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.adverse_reaction_risk.v2] WHERE e/ehr_status/subject/external_ref/id/value = '194303193454' AND e/ehr_status/subject/external_ref/namespace = '1.2.752.129.2.1.3.1'");
        assertAql(response, ToAqlResponse.AqlType.COMPOSITION,
                "SELECT c FROM EHR e CONTAINS COMPOSITION c CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.adverse_reaction_risk.v2] WHERE e/ehr_status/subject/external_ref/id/value = '194303193454' AND e/ehr_status/subject/external_ref/namespace = '1.2.752.129.2.1.3.1'");
    }

    @Test
    public void toAql_bySubjectWithoutNamespace_conditionVerificationStatusConfirmed() {
        final ToAqlResponse response = toAqlBySubject("Condition?verification-status=confirmed", SUBJECT_ID, null);
        assertAql(response, ToAqlResponse.AqlType.ENTRY,
                "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.problem_diagnosis.v1] WHERE e/ehr_status/subject/external_ref/id/value = '194303193454' AND h/data[at0001]/items[at0073]/value/defining_code/terminology_id/value = 'local' AND h/data[at0001]/items[at0073]/value/defining_code/code_string = 'at0076' AND h/data[at0001]/items[at0073]/value/value = 'Confirmed'");
        assertAql(response, ToAqlResponse.AqlType.COMPOSITION,
                "SELECT c from EHR e CONTAINS COMPOSITION c WHERE e/ehr_status/subject/external_ref/id/value = '194303193454' and c/content[openEHR-EHR-SECTION.adhoc.v1 and name/value='EPS Problems']/items[openEHR-EHR-EVALUATION.problem_diagnosis.v1]/data[at0001]/items[at0073]/value/defining_code/terminology_id/value = 'local' AND c/content[openEHR-EHR-SECTION.adhoc.v1 and name/value='EPS Problems']/items[openEHR-EHR-EVALUATION.problem_diagnosis.v1]/data[at0001]/items[at0073]/value/defining_code/code_string = 'at0076' AND c/content[openEHR-EHR-SECTION.adhoc.v1 and name/value='EPS Problems']/items[openEHR-EHR-EVALUATION.problem_diagnosis.v1]/data[at0001]/items[at0073]/value/value = 'Confirmed'");
    }

    /**
     * Every AQL of every EPS section, with and without search parameters, is scoped by the subject in the form Unio's
     * SubjectPredicateExtractor accepts, with and without a namespace.
     */
    @Test
    public void toAql_bySubject_everyEpsSection_isAcceptedByUnio() {
        final Set<String> entryTypes = new TreeSet<>();
        for (final String query : List.of("AllergyIntolerance", "AllergyIntolerance?manifestation=4386001",
                                          "Condition?verification-status=confirmed", "Procedure",
                                          "DeviceUseStatement?_include=DeviceUseStatement:device")) {
            for (final String namespace : new String[]{null, SUBJECT_NAMESPACE}) {
                final ToAqlResponse response = toAqlBySubject(query, SUBJECT_ID, namespace);
                Assert.assertTrue(query, response.getAqls() != null && !response.getAqls().isEmpty());
                FederationGatewaySubjectPredicateRule.assertAllScopedBySubject(response, SUBJECT_ID, namespace);
                Assert.assertTrue(query, response.getUnhandledParams() == null || response.getUnhandledParams()
                        .stream().noneMatch(p -> AqlPatientScope.PATIENT_SCOPE_PARAM.equals(p.getParamName())));
                response.getAqls().stream()
                        .filter(a -> a.getType() == ToAqlResponse.AqlType.ENTRY)
                        .forEach(a -> entryTypes.add(a.getAql().split(" ")[6]));
            }
        }
        Assert.assertEquals(Set.of("ACTION", "EVALUATION"), entryTypes);
    }

    @Test
    public void toAql_bySubject_aQuoteInTheIdentifierStaysInTheLiteral() {
        final ToAqlResponse response = toAqlBySubject("Procedure", "O'Neil-1", "urn:o'neil");
        assertAql(response, ToAqlResponse.AqlType.ENTRY,
                "SELECT h FROM EHR e CONTAINS ACTION h [openEHR-EHR-ACTION.procedure.v1] WHERE e/ehr_status/subject/external_ref/id/value = 'O\\'Neil-1' AND e/ehr_status/subject/external_ref/namespace = 'urn:o\\'neil'");
        FederationGatewaySubjectPredicateRule.assertAllScopedBySubject(response, "O'Neil-1", "urn:o'neil");
    }

    /**
     * A search value that tries to leave its string literal stays in it: the AQL is still scoped to the patient by
     * subject, accepted by Unio's rule, and compares the whole value; by ehrId it is still scoped too.
     */
    @Test
    public void toAql_aSearchValueCannotLeaveItsLiteral() {
        final String value = "x' OR e/ehr_id/value LIKE '*";
        final ToAqlResponse bySubject = toAqlBySubject("AllergyIntolerance?manifestation=" + value, SUBJECT_ID,
                                                       SUBJECT_NAMESPACE);
        Assert.assertNull(String.valueOf(bySubject.getUnhandledParams()), bySubject.getUnhandledParams());
        FederationGatewaySubjectPredicateRule.assertAllScopedBySubject(bySubject, SUBJECT_ID, SUBJECT_NAMESPACE);
        assertAql(bySubject, ToAqlResponse.AqlType.ENTRY,
                "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.adverse_reaction_risk.v2] WHERE e/ehr_status/subject/external_ref/id/value = '194303193454' AND e/ehr_status/subject/external_ref/namespace = '1.2.752.129.2.1.3.1' AND h/data[at0001]/items[openEHR-EHR-CLUSTER.adverse_reaction_event.v1]/items[at0006]/value/defining_code/code_string = 'x\\' OR e/ehr_id/value LIKE \\'*'");

        final ToAqlRequest byEhrId = new ToAqlRequest(null, "7d44b88c", "AllergyIntolerance?manifestation=" + value);
        final ToAqlResponse response = toAql.toAql(byEhrId);
        AqlPatientScope.apply(byEhrId, response);
        assertAql(response, ToAqlResponse.AqlType.ENTRY,
                "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.adverse_reaction_risk.v2] WHERE e/ehr_id/value='7d44b88c' AND h/data[at0001]/items[openEHR-EHR-CLUSTER.adverse_reaction_event.v1]/items[at0006]/value/defining_code/code_string = 'x\\' OR e/ehr_id/value LIKE \\'*'");
    }

    /** What OpenFhirEngine.toAql does: the request is checked, the AQLs generated, then scoped to the patient. */
    private ToAqlResponse toAqlBySubject(final String fhirFullUrl, final String id, final String namespace) {
        final ToAqlRequest request = new ToAqlRequest(null, null, fhirFullUrl, new ToAqlRequest.Subject(id, namespace));
        AqlPatientScope.validate(request);
        final ToAqlResponse response = toAql.toAql(request);
        AqlPatientScope.apply(request, response);
        return response;
    }

    private void assertAql(final ToAqlResponse response, final ToAqlResponse.AqlType type, final String expectedAql) {
        Assert.assertNotNull(response);
        final AqlResponse match = response.getAqls().stream()
                .filter(a -> a.getType() == type)
                .findFirst()
                .orElse(null);
        Assert.assertNotNull("No AQL of type " + type + " found in response", match);
        Assert.assertEquals(expectedAql, match.getAql());
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
