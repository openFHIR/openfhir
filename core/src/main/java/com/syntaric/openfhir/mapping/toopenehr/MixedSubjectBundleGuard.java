package com.syntaric.openfhir.mapping.toopenehr;

import com.syntaric.openfhir.operations.MappingIssueCollector;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * Detects Bundles whose entries refer to more than one subject before the {@code $toopenehr} mapping runs.
 *
 * <p>A Bundle always maps to a single Composition, and an openEHR Composition has no subject inside it —
 * the subject is EHR context, supplied at commit time outside the mapping. The engine therefore cannot
 * partition entries per patient; a mixed-subject Bundle (typically a searchset spanning patients) silently
 * folds every entry into one Composition. This guard makes that visible: it <b>warns and never blocks</b>,
 * because splitting per subject is the caller's (or a facade's) responsibility in front of the engine.
 */
@Slf4j
public final class MixedSubjectBundleGuard {

    /** Mirrors OperationOutcome issue type {@code multiple-matches}. */
    public static final String CODE_MULTIPLE_MATCHES = "multiple-matches";

    /** Same two child names {@code SubjectReferencePopulator} considers, to stay one convention. */
    private static final List<String> SUBJECT_CHILD_NAMES = List.of("subject", "patient");

    private MixedSubjectBundleGuard() {
    }

    /**
     * Inspects the Bundle's entries and reports a {@code multiple-matches} warning on the collector when
     * the entries reference more than one distinct subject, or the Bundle carries more than one Patient
     * entry. Never throws and never mutates the Bundle.
     */
    public static void check(final Bundle bundle, final MappingIssueCollector issueCollector) {
        final Set<String> subjectKeys = new LinkedHashSet<>();
        final Set<String> patientEntryKeys = new LinkedHashSet<>();
        int patientEntryCount = 0;

        for (final Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            final Resource resource = entry.getResource();
            if (resource == null) {
                continue;
            }
            if (resource instanceof Patient) {
                patientEntryCount++;
                if (!resource.getIdElement().isEmpty()) {
                    final IdType unqualified = resource.getIdElement().toUnqualifiedVersionless();
                    patientEntryKeys.add(unqualified.hasResourceType()
                                                 ? unqualified.getValue()
                                                 : "Patient/" + unqualified.getIdPart());
                }
                continue;
            }
            for (final Property property : resource.children()) {
                if (!SUBJECT_CHILD_NAMES.contains(property.getName())
                        || property.getTypeCode() == null
                        || !property.getTypeCode().startsWith("Reference")) {
                    continue;
                }
                for (final Base value : property.getValues()) {
                    if (value instanceof Reference reference) {
                        final String key = subjectKey(reference);
                        if (key != null) {
                            subjectKeys.add(key);
                        }
                    }
                }
            }
        }

        if (subjectKeys.size() <= 1 && patientEntryCount <= 1) {
            return;
        }

        final StringBuilder message = new StringBuilder("Bundle refers to more than one subject: ");
        if (subjectKeys.size() > 1) {
            message.append("entries reference ").append(subjectKeys.size()).append(" distinct subjects (")
                    .append(String.join(", ", subjectKeys)).append(")");
        }
        if (patientEntryCount > 1) {
            if (subjectKeys.size() > 1) {
                message.append("; additionally, ");
            }
            message.append("the Bundle contains ").append(patientEntryCount).append(" Patient entries");
            if (!patientEntryKeys.isEmpty()) {
                message.append(" (").append(String.join(", ", patientEntryKeys)).append(")");
            }
        }
        message.append(". One Bundle maps to one Composition committed to one EHR/subject — the engine cannot "
                               + "partition entries per patient, so all entries were folded into a single "
                               + "Composition. Partition the Bundle per subject before invoking $toopenehr.");
        if (bundle.getType() == Bundle.BundleType.SEARCHSET) {
            message.append(" This Bundle is a searchset; search results commonly span patients — split the "
                                   + "searchset per subject and submit one Bundle per patient.");
        }

        final String diagnostics = message.toString();
        log.warn(diagnostics);
        issueCollector.add(MappingIssueCollector.SEVERITY_WARNING, CODE_MULTIPLE_MATCHES, diagnostics);
    }

    /**
     * A comparable identity for a subject Reference: the relative-normalized literal reference when present,
     * otherwise {@code system|value} of an identifier-only reference. Display-only and empty references have
     * no comparable identity and yield {@code null}.
     */
    private static String subjectKey(final Reference reference) {
        final String literal = normalizeReference(reference.getReference());
        if (literal != null) {
            return literal;
        }
        if (reference.hasIdentifier() && StringUtils.isNotBlank(reference.getIdentifier().getValue())) {
            return StringUtils.defaultString(reference.getIdentifier().getSystem())
                    + "|" + reference.getIdentifier().getValue();
        }
        return null;
    }

    /**
     * Normalizes a literal reference to relative {@code Type/id} form so the same subject written as
     * {@code Patient/p1}, {@code #Patient/p1} or {@code https://host/fhir/Patient/p1} counts once.
     */
    private static String normalizeReference(final String reference) {
        if (StringUtils.isBlank(reference)) {
            return null;
        }
        final String stripped = reference.startsWith("#") ? reference.substring(1) : reference;
        final IdType id = new IdType(stripped);
        if (id.hasResourceType() && id.hasIdPart()) {
            return id.getResourceType() + "/" + id.getIdPart();
        }
        return stripped;
    }
}
