package com.syntaric.openfhir.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntaric.openfhir.OpenFhirAuthorizationContext;
import com.syntaric.openfhir.db.entity.BootstrapEntity;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.db.entity.FhirConnectModelEntity;
import com.syntaric.openfhir.db.repository.BootstrapRepository;
import com.syntaric.openfhir.fc.schema.Metadata;
import com.syntaric.openfhir.fc.schema.context.Context;
import com.syntaric.openfhir.fc.schema.context.ContextTemplate;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import com.syntaric.openfhir.manager.FhirConnectManager;
import com.syntaric.openfhir.manager.OptManager;
import com.syntaric.openfhir.producers.UserContextProducerInterface;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the bootstrap scan: what it creates, what it updates in place, what it skips, and how it copes
 * with ledger rows written before the ledger tracked entity ids.
 */
public class BootstrapServiceTest {

    private static final String TENANT = "default";
    private static final String MODEL_YAML = "metadata:\n  name: some-model\n";
    private static final String CONTEXT_YAML = "context:\n  template:\n    id: some.template.v0\n";

    @TempDir
    Path bootstrapDir;

    private BootstrapRepository bootstrapRepository;
    private FhirConnectManager fhirConnectManager;
    private OptManager optManager;
    private ObjectMapper yamlParser;
    private BootstrapService service;

    /**
     * Ledger rows the mocked repository has "persisted", so a test can assert what was written.
     */
    private List<BootstrapEntity> saved;

    @BeforeEach
    public void setUp() {
        bootstrapRepository = Mockito.mock(BootstrapRepository.class);
        fhirConnectManager = Mockito.mock(FhirConnectManager.class);
        optManager = Mockito.mock(OptManager.class);
        yamlParser = Mockito.mock(ObjectMapper.class);
        saved = new ArrayList<>();

        final UserContextProducerInterface userContextProducer = Mockito.mock(UserContextProducerInterface.class);
        final OpenFhirAuthorizationContext authContext = new OpenFhirAuthorizationContext();
        authContext.setTenant(TENANT);
        authContext.setUserId(TENANT);
        when(userContextProducer.getAuthContext()).thenReturn(authContext);

        when(bootstrapRepository.save(any())).thenAnswer(invocation -> {
            final BootstrapEntity entity = invocation.getArgument(0);
            saved.add(entity);
            return entity;
        });
        when(bootstrapRepository.findByPathAndTenant(anyString(), anyString())).thenReturn(Collections.emptyList());
        when(bootstrapRepository.findByFile(anyString())).thenReturn(Collections.emptyList());
        when(bootstrapRepository.findAllByTenant(anyString())).thenReturn(Collections.emptyList());

        service = new BootstrapService(bootstrapRepository, fhirConnectManager, optManager, yamlParser,
                                       userContextProducer);
        ReflectionTestUtils.setField(service, "bootstrapDir", bootstrapDir.toString());
        ReflectionTestUtils.setField(service, "recursivelyOpenDirectories", true);
    }

    @Test
    public void firstRunCreatesAndStoresEntityId() throws Exception {
        writeFile("some.yaml", MODEL_YAML);
        stubModelParsing();
        stubModelUpsert("model-id-1");

        final BootstrapSummary summary = service.runBootstrap("req");

        assertEquals(1, summary.getCreated());
        assertEquals(0, summary.getUpdated());
        // a create goes in with no target id
        verify(fhirConnectManager).upsertModelMapper(any(), isNull(), eq("req"));

        assertEquals(1, saved.size());
        final BootstrapEntity row = saved.get(0);
        assertEquals("model-id-1", row.getEntityId());
        assertEquals("MODEL", row.getEntityType());
        assertEquals("some.yaml", row.getPath());
        assertEquals("some.yaml", row.getFile());
        assertEquals(TENANT, row.getOrganisation());
        assertEquals(DigestUtils.sha256Hex(MODEL_YAML), row.getContentHash());

        assertEquals("CREATED", summary.getFiles().get(0).getOutcome());
        assertEquals("model-id-1", summary.getFiles().get(0).getEntityId());
    }

    @Test
    public void unchangedFileIsSkippedWithoutRepositoryWrite() throws Exception {
        writeFile("some.yaml", MODEL_YAML);
        existingLedgerRow("some.yaml", "some.yaml", DigestUtils.sha256Hex(MODEL_YAML), "model-id-1", "MODEL");

        final BootstrapSummary summary = service.runBootstrap("req");

        assertEquals(1, summary.getUnchanged());
        assertEquals(0, summary.getCreated());
        assertEquals(0, summary.getUpdated());
        verify(bootstrapRepository, never()).save(any());
        verify(fhirConnectManager, never()).upsertModelMapper(any(), any(), anyString());
        assertEquals("model-id-1", summary.getFiles().get(0).getEntityId());
    }

    @Test
    public void changedContentUpdatesTheSameEntityAndLedgerRow() throws Exception {
        writeFile("some.yaml", MODEL_YAML);
        final BootstrapEntity existing = existingLedgerRow("some.yaml", "some.yaml", DigestUtils.sha256Hex("older"),
                                                           "model-id-1", "MODEL");
        stubModelParsing();
        stubModelUpsert("model-id-1");

        final BootstrapSummary summary = service.runBootstrap("req");

        assertEquals(1, summary.getUpdated());
        assertEquals(0, summary.getCreated());
        // the stored id is passed through, so the engine updates in place rather than creating a duplicate
        verify(fhirConnectManager).upsertModelMapper(any(), eq("model-id-1"), eq("req"));

        assertEquals(1, saved.size());
        final BootstrapEntity row = saved.get(0);
        // same ledger row id => updated, not duplicated
        assertEquals(existing.getId(), row.getId());
        assertEquals(DigestUtils.sha256Hex(MODEL_YAML), row.getContentHash());
        assertEquals("model-id-1", row.getEntityId());
    }

    @Test
    public void legacyRowResolvesModelIdByNameInsteadOfCreatingDuplicate() throws Exception {
        writeFile("some.yaml", MODEL_YAML);
        // a pre-upgrade row: no path, no organisation, no hash, no entity id
        final BootstrapEntity legacy = new BootstrapEntity(UUID.randomUUID().toString(), "some.yaml", new Date());
        when(bootstrapRepository.findByFile("some.yaml")).thenReturn(List.of(legacy));

        stubModelParsing();
        stubModelUpsert("model-id-1");
        final FhirConnectModelEntity found = new FhirConnectModelEntity();
        found.setId("model-id-1");
        when(fhirConnectManager.findModelsByNames(anyList())).thenReturn(List.of(found));

        final BootstrapSummary summary = service.runBootstrap("req");

        assertEquals(1, summary.getUpdated());
        assertEquals(0, summary.getCreated());
        // id recovered from the natural key, so the uniqueness guard is not tripped and no second entity appears
        verify(fhirConnectManager).upsertModelMapper(any(), eq("model-id-1"), eq("req"));

        final BootstrapEntity row = saved.get(0);
        assertEquals(legacy.getId(), row.getId());
        // the previously missing fields are backfilled
        assertEquals("some.yaml", row.getPath());
        assertEquals(TENANT, row.getOrganisation());
        assertEquals("model-id-1", row.getEntityId());
        assertNotNull(row.getContentHash());
    }

    @Test
    public void legacyContextRowResolvesIdByTemplateId() throws Exception {
        writeFile("some.context.yaml", CONTEXT_YAML);
        final BootstrapEntity legacy = new BootstrapEntity(UUID.randomUUID().toString(), "some.context.yaml",
                                                           new Date());
        when(bootstrapRepository.findByFile("some.context.yaml")).thenReturn(List.of(legacy));

        final FhirConnectContext parsed = new FhirConnectContext();
        final Context context = new Context();
        final ContextTemplate template = new ContextTemplate();
        template.setId("some.template.v0");
        context.setTemplate(template);
        parsed.setContext(context);
        when(yamlParser.readValue(anyString(), eq(FhirConnectContext.class))).thenReturn(parsed);

        final FhirConnectContextEntity found = new FhirConnectContextEntity();
        found.setId("context-id-1");
        when(fhirConnectManager.findContextByTemplateId("some.template.v0")).thenReturn(found);
        when(fhirConnectManager.upsertContextMapper(any(), anyString(), anyString())).thenReturn(found);

        final BootstrapSummary summary = service.runBootstrap("req");

        assertEquals(1, summary.getUpdated());
        verify(fhirConnectManager).upsertContextMapper(any(), eq("context-id-1"), eq("req"));
        assertEquals("CONTEXT", saved.get(0).getEntityType());
    }

    @Test
    public void legacyFallbackDoesNotAdoptAnotherTenantsRow() throws Exception {
        writeFile("some.yaml", MODEL_YAML);
        // a pathless row belonging to a different tenant must not be mistaken for this tenant's legacy row
        final BootstrapEntity otherTenants = withOrganisation(BootstrapEntity.builder()
                .id(UUID.randomUUID().toString())
                .file("some.yaml")
                .date(new Date())
                .build(), "someone-else");
        when(bootstrapRepository.findByFile("some.yaml")).thenReturn(List.of(otherTenants));
        stubModelParsing();
        stubModelUpsert("model-id-1");

        final BootstrapSummary summary = service.runBootstrap("req");

        assertEquals(1, summary.getCreated());
        assertEquals(0, summary.getUpdated());
        // a fresh row for this tenant, not an overwrite of the other tenant's
        assertTrue(!otherTenants.getId().equals(saved.get(0).getId()));
        assertEquals(TENANT, saved.get(0).getOrganisation());
    }

    @Test
    public void sameFileNameInTwoSubfoldersYieldsTwoDistinctLedgerRows() throws Exception {
        Files.createDirectories(bootstrapDir.resolve("a"));
        Files.createDirectories(bootstrapDir.resolve("b"));
        Files.writeString(bootstrapDir.resolve("a/same.yaml"), MODEL_YAML, StandardCharsets.UTF_8);
        Files.writeString(bootstrapDir.resolve("b/same.yaml"), MODEL_YAML + "# b\n", StandardCharsets.UTF_8);
        stubModelParsing();
        stubModelUpsert("model-id-1");

        final BootstrapSummary summary = service.runBootstrap("req");

        assertEquals(2, summary.getCreated());
        assertEquals(2, saved.size());
        final List<String> paths = saved.stream().map(BootstrapEntity::getPath).sorted().toList();
        assertEquals(List.of("a/same.yaml", "b/same.yaml"), paths);
        // distinct ledger rows, not one row overwritten by the other
        assertTrue(!saved.get(0).getId().equals(saved.get(1).getId()));
    }

    @Test
    public void failingFileDoesNotAbortTheRunAndWritesNoRow() throws Exception {
        writeFile("bad.yaml", "bad: true\n");
        writeFile("good.yml", MODEL_YAML);
        stubModelParsing(); // broad stub first, so the specific one below takes precedence
        // keyed off content rather than call order, since listFiles() ordering is not guaranteed
        when(yamlParser.readValue(eq("bad: true\n"), eq(FhirConnectModel.class)))
                .thenThrow(new IllegalArgumentException("boom"));
        stubModelUpsert("model-id-2");

        final BootstrapSummary summary = service.runBootstrap("req");

        assertEquals(1, summary.getFailed());
        assertEquals(1, summary.getCreated());
        // only the file that succeeded is recorded, so the failed one is retried on the next run
        assertEquals(1, saved.size());
        assertEquals("model-id-2", saved.get(0).getEntityId());

        final BootstrapFileResult failure = summary.getFiles().stream()
                .filter(result -> "FAILED".equals(result.getOutcome()))
                .findFirst()
                .orElseThrow();
        assertEquals("boom", failure.getMessage());
        assertNull(failure.getEntityId());
    }

    @Test
    public void concurrentRunIsRejected() throws Exception {
        writeFile("some.yaml", MODEL_YAML);
        stubModelParsing();
        // while the first run is mid-flight, a second one from another thread must be rejected rather than
        // racing it — the lock is not reentrant-friendly across threads by design
        final List<Class<?>> thrown = new ArrayList<>();
        when(fhirConnectManager.upsertModelMapper(any(), any(), anyString())).thenAnswer(invocation -> {
            final Thread other = new Thread(() -> {
                try {
                    service.runBootstrap("nested");
                } catch (final Exception e) {
                    thrown.add(e.getClass());
                }
            });
            other.start();
            other.join();
            final FhirConnectModelEntity entity = new FhirConnectModelEntity();
            entity.setId("model-id-1");
            return entity;
        });

        service.runBootstrap("req");

        assertEquals(List.of(BootstrapAlreadyRunningException.class), thrown);
    }

    @Test
    public void orphanedLedgerEntriesAreReportedButLeftAlone() throws Exception {
        writeFile("present.yaml", MODEL_YAML);
        stubModelParsing();
        stubModelUpsert("model-id-1");
        final BootstrapEntity orphan = withOrganisation(BootstrapEntity.builder()
                .id(UUID.randomUUID().toString())
                .file("gone.yaml")
                .path("gone.yaml")
                .contentHash("hash")
                .entityId("model-id-9")
                .entityType("MODEL")
                .date(new Date())
                .build(), TENANT);
        when(bootstrapRepository.findAllByTenant(TENANT)).thenReturn(List.of(orphan));

        final BootstrapSummary summary = service.runBootstrap("req");

        // the orphan is only logged — nothing is deleted and it does not show up in the summary
        assertEquals(1, summary.getFiles().size());
        assertEquals("present.yaml", summary.getFiles().get(0).getPath());
        final ArgumentCaptor<BootstrapEntity> captor = ArgumentCaptor.forClass(BootstrapEntity.class);
        verify(bootstrapRepository, times(1)).save(captor.capture());
        assertEquals("present.yaml", captor.getValue().getPath());
    }

    @Test
    public void allOfTenantReturnsOnlyThisTenantsLedgerEntries() {
        final BootstrapEntity mine = withOrganisation(BootstrapEntity.builder()
                .id(UUID.randomUUID().toString())
                .file("mine.yaml")
                .path("mine.yaml")
                .date(new Date())
                .build(), TENANT);
        when(bootstrapRepository.findAllByTenant(TENANT)).thenReturn(List.of(mine));

        final List<BootstrapEntity> result = service.allOfTenant();

        assertEquals(1, result.size());
        assertEquals("mine.yaml", result.get(0).getPath());
        // the lookup is tenant-scoped at the repository level
        verify(bootstrapRepository).findAllByTenant(TENANT);
    }

    /**
     * {@code organisation} is inherited from UserBasedEntity, so it is outside Lombok's generated builder.
     */
    private BootstrapEntity withOrganisation(final BootstrapEntity entity, final String organisation) {
        entity.setOrganisation(organisation);
        return entity;
    }

    private void writeFile(final String name, final String contents) throws Exception {
        Files.writeString(bootstrapDir.resolve(name), contents, StandardCharsets.UTF_8);
    }

    private BootstrapEntity existingLedgerRow(final String path, final String file, final String hash,
                                              final String entityId, final String entityType) {
        final BootstrapEntity entity = withOrganisation(BootstrapEntity.builder()
                .id(UUID.randomUUID().toString())
                .file(file)
                .path(path)
                .contentHash(hash)
                .entityId(entityId)
                .entityType(entityType)
                .date(new Date())
                .build(), TENANT);
        when(bootstrapRepository.findByPathAndTenant(path, TENANT)).thenReturn(List.of(entity));
        return entity;
    }

    private void stubModelParsing() throws Exception {
        final FhirConnectModel model = new FhirConnectModel();
        final Metadata metadata = new Metadata();
        metadata.setName("some-model");
        model.setMetadata(metadata);
        when(yamlParser.readValue(anyString(), eq(FhirConnectModel.class))).thenReturn(model);
    }

    private void stubModelUpsert(final String id) {
        final FhirConnectModelEntity entity = new FhirConnectModelEntity();
        entity.setId(id);
        when(fhirConnectManager.upsertModelMapper(any(), any(), anyString())).thenReturn(entity);
    }

    @Test
    public void nonMatchingFilesAreIgnored() throws Exception {
        writeFile("readme.txt", "not a mapping");
        new File(bootstrapDir.toFile(), "subdir.yaml").mkdirs();

        final BootstrapSummary summary = service.runBootstrap("req");

        // the directory named *.yaml must not fall through into the suffix chain
        assertEquals(0, summary.getCreated());
        assertEquals(0, summary.getFailed());
        assertTrue(summary.getFiles().isEmpty());
    }

    /**
     * Readiness must stay false until something explicitly marks the startup scan done - a plain re-run through
     * {@code POST /$bootstrap} is not the startup scan and must not flip it.
     */
    @Test
    public void startupScanComplete_isFalseUntilMarked() {
        assertFalse(service.isStartupScanComplete());

        service.runBootstrap("req");
        assertFalse(service.isStartupScanComplete());

        service.markStartupScanComplete();
        assertTrue(service.isStartupScanComplete());
    }

    /**
     * The runner reports readiness even when the scan throws, so an unparseable fixture cannot leave the
     * application permanently advertising itself as not ready.
     */
    @Test
    public void runner_marksComplete_evenWhenScanThrows() {
        final BootstrapService throwing = Mockito.mock(BootstrapService.class);
        when(throwing.runBootstrap(anyString())).thenThrow(new RuntimeException("boom"));

        final BootstrapRunner runner = new BootstrapRunner(throwing);
        assertThrows(RuntimeException.class, () -> runner.run(null));

        verify(throwing).markStartupScanComplete();
    }

    /**
     * The happy path: a successful startup scan leaves the application ready.
     */
    @Test
    public void runner_marksComplete_afterSuccessfulScan() {
        final BootstrapService ok = Mockito.mock(BootstrapService.class);

        new BootstrapRunner(ok).run(null);

        verify(ok).runBootstrap(anyString());
        verify(ok).markStartupScanComplete();
    }
}
