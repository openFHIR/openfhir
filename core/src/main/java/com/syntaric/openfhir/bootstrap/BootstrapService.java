package com.syntaric.openfhir.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntaric.openfhir.db.entity.BootstrapEntity;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.db.entity.FhirConnectModelEntity;
import com.syntaric.openfhir.db.entity.OptEntity;
import com.syntaric.openfhir.db.repository.BootstrapRepository;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import com.syntaric.openfhir.manager.FhirConnectManager;
import com.syntaric.openfhir.manager.OptManager;
import com.syntaric.openfhir.producers.UserContextProducerInterface;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Scans the configured bootstrap directory and creates or updates the model mappers, context mappers and
 * operational templates it finds there.
 * <p>
 * Every applied file is recorded in the bootstrap ledger ({@link BootstrapEntity}) together with a SHA-256 hash of
 * its content and the id of the entity it created, which is what makes a re-run able to tell an unchanged file
 * (skip) from a changed one (update the very same entity, in place) from a new one (create).
 */
@Component
@Slf4j
public class BootstrapService {

    static final String MODEL_SUFFIX = ".yaml";
    static final String MODEL_SUFFIX2 = ".yml";
    static final String CONTEXT_SUFFIX = "context.yaml";
    static final String CONTEXT_SUFFIX2 = "context.yml";
    static final String OPT_SUFFIX = ".opt";

    @Value("${openfhir.bootstrap.dir:/app/bootstrap/}")
    private String bootstrapDir;

    @Value("${openfhir.bootstrap.recursively-open-directories:true}")
    private boolean recursivelyOpenDirectories;

    private final BootstrapRepository bootstrapRepository;
    private final FhirConnectManager fhirConnectManager;
    private final OptManager optManager;
    private final ObjectMapper yamlParser;
    private final UserContextProducerInterface userContextProducer;

    private final ReentrantLock lock = new ReentrantLock();

    @Autowired
    public BootstrapService(final BootstrapRepository bootstrapRepository,
                            final FhirConnectManager fhirConnectManager,
                            final OptManager optManager,
                            final ObjectMapper yamlParser,
                            final UserContextProducerInterface userContextProducer) {
        this.bootstrapRepository = bootstrapRepository;
        this.fhirConnectManager = fhirConnectManager;
        this.optManager = optManager;
        this.yamlParser = yamlParser;
        this.userContextProducer = userContextProducer;
    }

    /**
     * Runs a full scan of the bootstrap directory.
     *
     * @param reqId request id, threaded through to the managers
     * @return summary of what was created, updated, left unchanged or failed
     * @throws BootstrapAlreadyRunningException if another scan is in progress
     */
    public BootstrapSummary runBootstrap(final String reqId) {
        if (!lock.tryLock()) {
            throw new BootstrapAlreadyRunningException("A bootstrap run is already in progress.");
        }
        try {
            return doRunBootstrap(reqId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * All bootstrap ledger entries of the logged-in tenant, i.e. the files that have been bootstrapped so far and
     * which entity each of them created.
     */
    public List<BootstrapEntity> allOfTenant() {
        final String tenant = userContextProducer.getAuthContext().getTenant();
        if (tenant == null) {
            return List.of();
        }
        final List<BootstrapEntity> all = bootstrapRepository.findAllByTenant(tenant);
        return all == null ? List.of() : all;
    }

    private BootstrapSummary doRunBootstrap(final String reqId) {
        final BootstrapSummary summary = BootstrapSummary.builder().files(new ArrayList<>()).build();
        if (bootstrapDir == null) {
            log.debug("No bootstrap dir defined.");
            return summary;
        }
        final File root = new File(bootstrapDir);
        if (!root.isDirectory()) {
            log.warn("Defined bootstrap directory '{}' is not a directory.", bootstrapDir);
            return summary;
        }
        if (root.listFiles() == null) {
            log.warn("No files in the bootstrap dir. Aborting.");
            return summary;
        }

        final String tenant = userContextProducer.getAuthContext().getTenant();
        final Set<String> seenPaths = new HashSet<>();
        runFolder(root, root, tenant, reqId, summary, seenPaths);

        logOrphans(tenant, seenPaths);
        log.info("Bootstrap finished: {} created, {} updated, {} unchanged, {} failed",
                 summary.getCreated(), summary.getUpdated(), summary.getUnchanged(), summary.getFailed());
        return summary;
    }

    private void runFolder(final File root, final File folder, final String tenant, final String reqId,
                           final BootstrapSummary summary, final Set<String> seenPaths) {
        for (final File file : Objects.requireNonNull(folder.listFiles())) {
            final String fileName = file.getName();
            if (fileName.isEmpty()) {
                continue;
            }
            if (file.isDirectory()) {
                if (!recursivelyOpenDirectories) {
                    log.warn(
                            "Found a folder {} in a bootstrap.dir location, set 'openfhir.bootstrap.recursively-open-directories' to true if you want openFHIR to look into sub-folders",
                            fileName);
                } else {
                    runFolder(root, file, tenant, reqId, summary, seenPaths);
                }
                continue;
            }
            final FileType fileType = classify(fileName);
            if (fileType == null) {
                log.warn(
                        "Found file '{}' doesn't match any pattern. If you want it to be bootstrapped, it has to end with {} or {} or {}",
                        fileName, MODEL_SUFFIX, CONTEXT_SUFFIX, OPT_SUFFIX);
                continue;
            }
            final String relativePath = relativePath(root, file);
            seenPaths.add(relativePath);
            summary.add(apply(file, relativePath, fileType, tenant, reqId));
        }
    }

    private FileType classify(final String fileName) {
        if (fileName.endsWith(CONTEXT_SUFFIX) || fileName.endsWith(CONTEXT_SUFFIX2)) {
            return FileType.CONTEXT;
        }
        if (fileName.endsWith(MODEL_SUFFIX) || fileName.endsWith(MODEL_SUFFIX2)) {
            return FileType.MODEL;
        }
        if (fileName.endsWith(OPT_SUFFIX)) {
            return FileType.OPT;
        }
        return null;
    }

    /**
     * Path of the file relative to the bootstrap dir, with separators normalized to '/' so the ledger key is
     * stable across platforms.
     */
    private String relativePath(final File root, final File file) {
        final Path relative = root.toPath().toAbsolutePath().normalize()
                .relativize(file.toPath().toAbsolutePath().normalize());
        return relative.toString().replace(File.separatorChar, '/');
    }

    private BootstrapFileResult apply(final File file, final String relativePath, final FileType fileType,
                                      final String tenant, final String reqId) {
        final String fileName = file.getName();
        try {
            final String contents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            final String hash = DigestUtils.sha256Hex(contents);
            final BootstrapEntity existing = resolveExisting(relativePath, fileName, tenant);

            if (existing != null && hash.equals(existing.getContentHash())) {
                log.info("Bootstrap: UNCHANGED {} -> {} {}", relativePath, existing.getEntityType(),
                         existing.getEntityId());
                return BootstrapFileResult.builder()
                        .path(relativePath)
                        .outcome(Outcome.UNCHANGED.name())
                        .entityType(existing.getEntityType())
                        .entityId(existing.getEntityId())
                        .build();
            }

            final Outcome outcome = existing == null ? Outcome.CREATED : Outcome.UPDATED;
            final String targetId = existing == null ? null : resolveTargetId(existing, contents, fileType);
            final String entityId = upsert(contents, fileType, targetId, reqId);

            saveLedgerEntry(existing, relativePath, fileName, hash, entityId, fileType, tenant);

            if (outcome == Outcome.CREATED) {
                log.info("Bootstrap: CREATED   {} -> {} {}", relativePath, fileType, entityId);
            } else {
                log.info("Bootstrap: UPDATED   {} -> {} {} (content changed since {})", relativePath, fileType,
                         entityId, existing.getDate());
            }
            return BootstrapFileResult.builder()
                    .path(relativePath)
                    .outcome(outcome.name())
                    .entityType(fileType.name())
                    .entityId(entityId)
                    .build();
        } catch (final Exception e) {
            log.error("Bootstrap: FAILED    {} : {}", relativePath, e.getMessage(), e);
            return BootstrapFileResult.builder()
                    .path(relativePath)
                    .outcome(Outcome.FAILED.name())
                    .entityType(fileType.name())
                    .message(e.getMessage())
                    .build();
        }
    }

    /**
     * Looks the ledger row up by the relative path first; failing that, falls back to the bare filename so rows
     * written before this feature existed (which have neither {@code path} nor {@code organisation}) are adopted
     * and upgraded rather than duplicated.
     */
    private BootstrapEntity resolveExisting(final String relativePath, final String fileName, final String tenant) {
        if (tenant != null) {
            final List<BootstrapEntity> byPath = bootstrapRepository.findByPathAndTenant(relativePath, tenant);
            if (byPath != null && !byPath.isEmpty()) {
                return byPath.get(0);
            }
        }
        final List<BootstrapEntity> byFile = bootstrapRepository.findByFile(fileName);
        if (byFile == null) {
            return null;
        }
        return byFile.stream()
                // a legacy row has no path at all; a row that has one belongs to a different file of the same name
                .filter(entity -> StringUtils.isBlank(entity.getPath()))
                // ...and no organisation either, so a row belonging to another tenant is never adopted
                .filter(entity -> StringUtils.isBlank(entity.getOrganisation())
                        || entity.getOrganisation().equals(tenant))
                .findFirst()
                .orElse(null);
    }

    /**
     * Id to update in place. For rows written by this feature that is simply the stored entity id; for legacy rows
     * it has to be recovered from the file's natural key, otherwise the create path would trip over the uniqueness
     * guards. A null result means "create" — correct when the entity was deleted out from under the ledger.
     */
    private String resolveTargetId(final BootstrapEntity existing, final String contents, final FileType fileType)
            throws Exception {
        if (StringUtils.isNotBlank(existing.getEntityId())) {
            return existing.getEntityId();
        }
        switch (fileType) {
            case MODEL -> {
                final FhirConnectModel model = yamlParser.readValue(contents, FhirConnectModel.class);
                final List<FhirConnectModelEntity> found = fhirConnectManager.findModelsByNames(
                        List.of(model.getMetadata().getName()));
                return found == null || found.isEmpty() ? null : found.get(0).getId();
            }
            case CONTEXT -> {
                final FhirConnectContext context = yamlParser.readValue(contents, FhirConnectContext.class);
                final FhirConnectContextEntity found = fhirConnectManager.findContextByTemplateId(
                        context.getContext().getTemplate().getId());
                return found == null ? null : found.getId();
            }
            case OPT -> {
                // templateIdOf already normalizes exactly the way OptService.upsert does when persisting
                final OptEntity found = optManager.byTemplateIdAndOrganization(optManager.templateIdOf(contents));
                return found == null ? null : found.getId();
            }
            default -> {
                return null;
            }
        }
    }

    private String upsert(final String contents, final FileType fileType, final String targetId, final String reqId)
            throws Exception {
        return switch (fileType) {
            case MODEL -> fhirConnectManager
                    .upsertModelMapper(yamlParser.readValue(contents, FhirConnectModel.class), targetId, reqId)
                    .getId();
            case CONTEXT -> fhirConnectManager
                    .upsertContextMapper(yamlParser.readValue(contents, FhirConnectContext.class), targetId, reqId)
                    .getId();
            // OptService.upsert returns a copy with redacted content, but the id on it is the real one
            case OPT -> optManager.upsert(contents, targetId, reqId).getId();
        };
    }

    private void saveLedgerEntry(final BootstrapEntity existing, final String relativePath, final String fileName,
                                 final String hash, final String entityId, final FileType fileType,
                                 final String tenant) {
        final Date now = new Date();
        final BootstrapEntity entry = BootstrapEntity.builder()
                // reuse the row id on update so the entry is updated, not duplicated
                .id(existing == null ? UUID.randomUUID().toString() : existing.getId())
                .file(fileName)
                .path(relativePath)
                .contentHash(hash)
                .entityId(entityId)
                .entityType(fileType.name())
                .date(now)
                .build();
        // the UserBasedEntity fields are not part of the generated builder, so they are set here
        entry.setOrganisation(tenant);
        entry.setUser(userContextProducer.getAuthContext().getUserId());
        entry.setCreated(existing == null || existing.getCreated() == null ? now : existing.getCreated());
        entry.setUpdated(now);
        bootstrapRepository.save(entry);
    }

    private void logOrphans(final String tenant, final Set<String> seenPaths) {
        if (tenant == null) {
            return;
        }
        final List<BootstrapEntity> all = bootstrapRepository.findAllByTenant(tenant);
        if (all == null || all.isEmpty()) {
            return;
        }
        final List<String> orphans = all.stream()
                .map(BootstrapEntity::getPath)
                .filter(StringUtils::isNotBlank)
                .filter(path -> !seenPaths.contains(path))
                .distinct()
                .collect(Collectors.toList());
        if (orphans.isEmpty()) {
            return;
        }
        log.warn("Bootstrap: {} previously bootstrapped file(s) no longer present on disk: {}. "
                         + "Their entities remain in the engine.", orphans.size(), orphans);
    }

    enum FileType {
        MODEL, CONTEXT, OPT
    }

    enum Outcome {
        CREATED, UPDATED, UNCHANGED, FAILED
    }
}
