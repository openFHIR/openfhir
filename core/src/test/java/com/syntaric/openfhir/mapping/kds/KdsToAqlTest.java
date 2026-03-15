package com.syntaric.openfhir.mapping.kds;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntaric.openfhir.OpenFhirMappingContext;
import com.syntaric.openfhir.TestOpenFhirMappingContext;
import com.syntaric.openfhir.aql.ToAqlRequest;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.db.repository.FhirConnectContextRepository;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.mapping.helpers.AqlToFlatPathConverter;
import com.syntaric.openfhir.mapping.helpers.HelpersCreator;
import com.syntaric.openfhir.mapping.toaql.OpenEhrAqlPopulator;
import com.syntaric.openfhir.mapping.toaql.ToAql;
import com.syntaric.openfhir.mapping.toaql.ToAqlMappingEngine;
import com.syntaric.openfhir.producers.NoOpUserContextProducer;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

@Slf4j
public class KdsToAqlTest {

    AutoCloseable closeable;

    @Mock
    protected FhirConnectContextRepository contextRepository;

    @Mock
    protected OpenEhrCachedUtils openEhrCachedUtils;

    final String DIR = getClass().getResource("/kds/").getFile();
    final String CONTEXT_DIR = getClass().getResource("/kds/core/projects/").getFile();

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
        doReturn(allContextEntities).when(contextRepository).findByTenant(any());

        for (String contextFile : contextFiles()) {
            try {
                final FhirConnectContext context = parseContext(contextFile);
                final OPERATIONALTEMPLATE operationalTemplate = getOperationalTemplate(contextFile);
                repo.initRepository(context, operationalTemplate, DIR);

                final FhirConnectContextEntity toBeReturned = new FhirConnectContextEntity();
                toBeReturned.setFhirConnectContext(context);

                final String templateId = operationalTemplate.getTemplateId().getValue();
                final String normalizedTemplateId = OpenFhirMappingContext.normalizeTemplateId(templateId);
                doReturn(toBeReturned).when(contextRepository).findByTemplateIdAndTenant(eq(normalizedTemplateId), any());

                doReturn(new OPTParser(operationalTemplate).parse()).when(openEhrCachedUtils).parseWebTemplate(eq(operationalTemplate));
                doReturn(operationalTemplate).when(openEhrCachedUtils).getOperationalTemplate(any(), eq(normalizedTemplateId));

            } catch (Exception e) {
                log.error("{}", e.getMessage());
            }
        }

        final HelpersCreator helpersCreator1 = new HelpersCreator(repo, new AqlToFlatPathConverter(
                new OpenFhirStringUtils(),
                new OpenFhirMapperUtils()), new OpenFhirStringUtils());
        toAql = new ToAql(contextRepository, new OpenFhirMapperUtils(), new NoOpUserContextProducer(), repo, new ToAqlMappingEngine(new OpenEhrAqlPopulator()), helpersCreator1,
                openEhrCachedUtils, null);
    }

    @Test
    public void limitByProfile_medikatonseintrag() {
        final ToAqlRequest toAqlRequest = new ToAqlRequest(null,
                "123", "MedicationStatement?status=final&_profile=https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationStatement");
        final ToAqlResponse aql = toAql.toAql(toAqlRequest);
        final ToAqlResponse.AqlResponse compositionAql = aql.getAqls().stream().filter(a -> a.getType() == ToAqlResponse.AqlType.COMPOSITION)
                .findAny().orElse(null);
        final ToAqlResponse.AqlResponse entryAql = aql.getAqls().stream().filter(a -> a.getType() == ToAqlResponse.AqlType.ENTRY)
                .findAny().orElse(null);
        Assert.assertEquals("SELECT c from EHR e CONTAINS COMPOSITION c CONTAINS OBSERVATION [openEHR-EHR-OBSERVATION.medication_statement.v0] WHERE e/ehr_id/value='{{ehrid}}' and c/context/other_context[at0005]/items[openEHR-EHR-CLUSTER.case_identification.v0]/items[at0003]/value = 'final'", compositionAql.getAql());
        Assert.assertNull(entryAql); // because its a contex aql
    }

    @Test
    public void limitByProfile_medikatonseintrag_category() {
        final ToAqlRequest toAqlRequest = new ToAqlRequest(null,
                "123", "MedicationStatement?status=final&category=456&_profile=https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationStatement");
        final ToAqlResponse aql = toAql.toAql(toAqlRequest);
        final ToAqlResponse.AqlResponse compositionAql = aql.getAqls().stream().filter(a -> a.getType() == ToAqlResponse.AqlType.COMPOSITION)
                .findAny().orElse(null);
        final ToAqlResponse.AqlResponse entryAql = aql.getAqls().stream().filter(a -> a.getType() == ToAqlResponse.AqlType.ENTRY)
                .findAny().orElse(null);
        Assert.assertEquals("SELECT c from EHR e CONTAINS COMPOSITION c CONTAINS OBSERVATION [openEHR-EHR-OBSERVATION.medication_statement.v0] WHERE e/ehr_id/value='{{ehrid}}' and c/context/other_context[at0005]/items[openEHR-EHR-CLUSTER.case_identification.v0]/items[at0003]/value = 'final' AND c/content[openEHR-EHR-OBSERVATION.medication_statement.v0]/protocol[at0004]/items[openEHR-EHR-CLUSTER.entry_category.v0]/items[at0002]/value = '456'", compositionAql.getAql());
        Assert.assertNull(entryAql); // because its a contex aql
    }

    @Test
    public void limitByProfile_medikatonseintrag_archetypeonly() {
        final ToAqlRequest toAqlRequest = new ToAqlRequest(null,
                "123", "MedicationStatement");
        final ToAqlResponse aql = toAql.toAql(toAqlRequest);
        final ToAqlResponse.AqlResponse compositionAql = aql.getAqls().stream().filter(a -> a.getType() == ToAqlResponse.AqlType.COMPOSITION)
                .findAny().orElse(null);
        final ToAqlResponse.AqlResponse entryAql = aql.getAqls().stream().filter(a -> a.getType() == ToAqlResponse.AqlType.ENTRY)
                .findAny().orElse(null);
        Assert.assertEquals("SELECT c FROM EHR e CONTAINS COMPOSITION c CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.medication_statement.v0] WHERE e/ehr_id/value='{{ehrid}}'", compositionAql.getAql());
        Assert.assertEquals("SELECT h FROM EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.medication_statement.v0] WHERE e/ehr_id/value='{{ehrid}}'", entryAql.getAql());
    }

    private OPERATIONALTEMPLATE getOperationalTemplate(final String contextFilePath) {
        final String dirName = new File(contextFilePath).getParentFile().getName();
        final File optDir = new File(DIR, dirName);
        final File[] optFiles = optDir.listFiles(f -> f.getName().endsWith(".opt"));
        if (optFiles == null || optFiles.length == 0) {
            throw new RuntimeException("No .opt file found in " + optDir.getAbsolutePath());
        }
        try {
            return TemplateDocument.Factory.parse(optFiles[0]).getTemplate();
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
            } else if (file.getName().endsWith("context.yaml") || file.getName().endsWith("context.xml")) {
                result.add(file.getAbsolutePath());
            }
        }
    }

}
