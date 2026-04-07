package com.syntaric.openfhir.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntaric.openfhir.manager.FhirConnectManager;
import com.syntaric.openfhir.manager.OptManager;
import com.syntaric.openfhir.db.entity.BootstrapEntity;
import com.syntaric.openfhir.db.repository.BootstrapRepository;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BootstrapRunner implements ApplicationRunner {

    @Value("${bootstrap.dir:/app/bootstrap/}")
    private String bootstrapDir;

    @Value("${bootstrap.recursively-open-directories:true}")
    private boolean recursivelyOpenDirectories;

    final String MODEL_SUFFIX = ".yaml";
    final String MODEL_SUFFIX2 = ".yml";

    final String CONTEXT_SUFFIX = "context.yaml";
    final String CONTEXT_SUFFIX2 = "context.yml";
    final String OPT_SUFFIX = ".opt";

    private final BootstrapRepository bootstrapRepository;
    private final FhirConnectManager fhirConnectManager;
    private final OptManager optManager;
    private final ObjectMapper yamlParser;

    @Autowired
    public BootstrapRunner(final BootstrapRepository bootstrapRepository,
                           final FhirConnectManager fhirConnectManager,
                           final OptManager optManager,
                           final ObjectMapper yamlParser) {
        this.bootstrapRepository = bootstrapRepository;
        this.fhirConnectManager = fhirConnectManager;
        this.optManager = optManager;
        this.yamlParser = yamlParser;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (bootstrapDir == null) {
            log.debug("No bootstrap dir defined.");
            return;
        }
        if (!new File(bootstrapDir).isDirectory()) {
            log.warn("Defined bootstrap directory '{}' is not a directory.", bootstrapDir);
            return;
        }
        if (new File(bootstrapDir).listFiles() == null) {
            log.warn("No files in the bootstrap dir. Aborting.");
            return;
        }
        runFolder(bootstrapDir);
    }

    private void runFolder(final String folder) {
        for (final File file : Objects.requireNonNull(new File(folder).listFiles())) {
            final String fileName = file.getName();
            if (fileName.isEmpty()) {
                continue;
            }
            if (file.isDirectory()) {
                if (!recursivelyOpenDirectories) {
                    log.warn(
                            "Found a folder {} in a bootstrap.dir location, set 'bootstrap.recursively-open-directories' to true if you want openFHIR to look into sub-folders",
                            file.getName());
                    continue;
                } else {
                    runFolder(file.getAbsolutePath());
                }
            }
            if (fileName.endsWith(CONTEXT_SUFFIX) || fileName.endsWith(CONTEXT_SUFFIX2)) {
                log.info("Found a file '{}' that matches context suffix", fileName);
                runIfNotRanYet(file, FileType.CONTEXT);
            } else if (fileName.endsWith(MODEL_SUFFIX) || fileName.endsWith(MODEL_SUFFIX2)) {
                log.info("Found a YML file '{}' that is not .context, therefore assuming a model mapper", fileName);
                runIfNotRanYet(file, FileType.MODEL);
            } else if (fileName.endsWith(OPT_SUFFIX)) {
                log.info("Found a file '{}' that matches opt suffix", fileName);
                runIfNotRanYet(file, FileType.OPT);
            }  else {
                log.warn(
                        "Found file '{}' doesn't match any pattern. If you want it to be bootstrapped, it has to end with {} or {} or {}",
                        fileName, MODEL_SUFFIX, CONTEXT_SUFFIX, OPT_SUFFIX);
            }
        }
    }

    private void runIfNotRanYet(final File file, final FileType fileType) {
        final String fileName = file.getName();
        final List<BootstrapEntity> byFile = bootstrapRepository.findByFile(fileName);
        if (byFile != null && !byFile.isEmpty()) {
            log.info("File {} already bootstrapped at {}. Skipping.", fileName, byFile.get(0).getDate());
            return;
        }
        try {
            final String fileContents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);

            if (fileType == FileType.MODEL) {
                log.info("Creating model file {} from bootstrap.", fileName);
                fhirConnectManager.upsertModelMapper(yamlParser.readValue(fileContents, FhirConnectModel.class), null, "bootstrap-req");
            } else if (fileType == FileType.CONTEXT) {
                log.info("Creating context file {} from bootstrap.", fileName);
                fhirConnectManager.upsertContextMapper(yamlParser.readValue(fileContents, FhirConnectContext.class), null, "bootstrap-req");
            } else if (fileType == FileType.OPT) {
                log.info("Creating OPT file {} from bootstrap.", fileName);
                optManager.upsert(fileContents, null, "bootstrap-req");
            }

            bootstrapRepository.save(new BootstrapEntity(UUID.randomUUID().toString(),
                                                         fileName,
                                                         new java.util.Date()));
        } catch (final Exception e) {
            log.error("File {} couldn't be bootstrapped due to ", file, e);
        }
    }

    enum FileType {
        MODEL, CONTEXT, OPT
    }
}
