package com.syntaric.openfhir.metrics;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * File-based {@link MappingMetricsLogger} that appends one CSV line per metric record.
 *
 * <p>Activated when {@code openfhir.metrics.file.enabled=true} is set. Becomes {@code @Primary}
 * over the default {@link Slf4jMappingMetricsLogger}.
 *
 * <p>Minimal configuration (application.yaml):
 * <pre>
 * openfhir:
 *   metrics:
 *     file:
 *       enabled: true
 *       path: /var/log/openfhir/metrics.csv
 * </pre>
 *
 * <p>Optional settings:
 * <pre>
 * openfhir:
 *   metrics:
 *     file:
 *       enabled: true
 *       path: /var/log/openfhir/metrics.csv
 *       append: true          # default true — set false to truncate on startup
 *       write-header: true    # default true — writes CSV header if file is new/empty
 * </pre>
 *
 * <p>Output format (CSV):
 * <pre>
 * timestamp,section,context,elapsed_ms
 * 2026-03-30T14:05:01.123,toFhir.total,template=blood-pressure,42
 * </pre>
 */
@Slf4j
@Primary
@Component
@ConditionalOnProperty(prefix = "openfhir.metrics.file", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FileMappingMetricsLogger.FileMetricsProperties.class)
public class FileMappingMetricsLogger implements MappingMetricsLogger {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final String CSV_HEADER = "timestamp,section,context,elapsed_ms";

    private final BufferedWriter writer;

    public FileMappingMetricsLogger(final FileMetricsProperties props) throws IOException {
        final Path filePath = Paths.get(props.getPath());

        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }

        final boolean isNewOrEmpty = !Files.exists(filePath) || Files.size(filePath) == 0;
        final StandardOpenOption[] openOptions = props.isAppend()
                ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE};

        this.writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, openOptions);

        if (props.isWriteHeader() && isNewOrEmpty) {
            writer.write(CSV_HEADER);
            writer.newLine();
            writer.flush();
        }

        log.info("FileMappingMetricsLogger writing to: {}", filePath.toAbsolutePath());
    }

    @Override
    public void record(final String section, final String context, final long elapsedMs) {
        final String line = String.format("%s,%s,%s,%d",
                LocalDateTime.now().format(TIMESTAMP_FMT),
                escapeCsv(section),
                escapeCsv(context),
                elapsedMs);
        try {
            synchronized (writer) {
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (final IOException e) {
            log.error("FileMappingMetricsLogger failed to write metric: {}", line, e);
        }
    }

    @PreDestroy
    public void close() {
        try {
            writer.close();
        } catch (final IOException e) {
            log.warn("FileMappingMetricsLogger failed to close writer cleanly", e);
        }
    }

    /** Wraps a value in quotes and escapes internal quotes if the value contains a comma or quote. */
    private static String escapeCsv(final String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @ConfigurationProperties(prefix = "openfhir.metrics.file")
    public static class FileMetricsProperties {

        /** Absolute or relative path to the output file. */
        private String path = "metrics.csv";

        /** Whether to append to an existing file (true) or truncate on startup (false). */
        private boolean append = true;

        /** Write a CSV header line when the file is new or empty. */
        private boolean writeHeader = true;

        public String getPath() { return path; }
        public void setPath(final String path) { this.path = path; }

        public boolean isAppend() { return append; }
        public void setAppend(final boolean append) { this.append = append; }

        public boolean isWriteHeader() { return writeHeader; }
        public void setWriteHeader(final boolean writeHeader) { this.writeHeader = writeHeader; }
    }
}
