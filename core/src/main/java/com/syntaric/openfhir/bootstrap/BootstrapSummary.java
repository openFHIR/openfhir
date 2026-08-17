package com.syntaric.openfhir.bootstrap;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of a full bootstrap directory scan, returned by {@link BootstrapService#runBootstrap(String)} and
 * serialized as the body of {@code POST /$bootstrap}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BootstrapSummary {

    private int created;
    private int updated;
    private int unchanged;
    private int failed;

    @Builder.Default
    private List<BootstrapFileResult> files = new ArrayList<>();

    void add(final BootstrapFileResult result) {
        files.add(result);
        switch (BootstrapService.Outcome.valueOf(result.getOutcome())) {
            case CREATED -> created++;
            case UPDATED -> updated++;
            case UNCHANGED -> unchanged++;
            case FAILED -> failed++;
        }
    }
}
