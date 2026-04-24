package com.syntaric.openfhir.producers;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.fhirpath.IFhirPathEvaluationContext;
import com.syntaric.openfhir.fc.schema.Spec;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that provides version-specific {@link FhirContext} and {@link IFhirPath} instances.
 * Instances are created lazily and cached per FHIR version.
 */
@Component
@Slf4j
public class FhirContextRegistry {

    private final Map<Spec.Version, FhirContext> contextCache = new ConcurrentHashMap<>();
    private final Map<Spec.Version, IFhirPath> fhirPathCache = new ConcurrentHashMap<>();

    public FhirContext getContext(final Spec.Version version) {
        return contextCache.computeIfAbsent(version, this::createFhirContext);
    }

    public IFhirPath getFhirPath(final Spec.Version version) {
        return fhirPathCache.computeIfAbsent(version, v -> createFhirPath(getContext(v)));
    }

    /**
     * Convenience method for callers that don't have a version — defaults to R4.
     */
    public FhirContext getDefaultContext() {
        return getContext(Spec.Version.R4);
    }

    /**
     * Convenience method for callers that don't have a version — defaults to R4.
     */
    public IFhirPath getDefaultFhirPath() {
        return getFhirPath(Spec.Version.R4);
    }

    private FhirContext createFhirContext(final Spec.Version version) {
        log.info("Creating FhirContext for FHIR version {}", version);
        switch (version) {
            case STU3:
                return FhirContext.forDstu3();
            case R4B:
                return FhirContext.forR4B();
            case R5:
                return FhirContext.forR5();
            case R4:
            default:
                return FhirContext.forR4();
        }
    }

    private IFhirPath createFhirPath(final FhirContext ctx) {
        final IFhirPath fhirPath = ctx.newFhirPath();
        fhirPath.setEvaluationContext(new IFhirPathEvaluationContext() {
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                if (theContext == null) {
                    return null;
                }
                if (theContext instanceof org.hl7.fhir.dstu3.model.Reference) {
                    return ((org.hl7.fhir.dstu3.model.Reference) theContext).getResource();
                }
                if (theContext instanceof org.hl7.fhir.r4.model.Reference) {
                    return ((org.hl7.fhir.r4.model.Reference) theContext).getResource();
                }
                if (theContext instanceof org.hl7.fhir.r4b.model.Reference) {
                    return ((org.hl7.fhir.r4b.model.Reference) theContext).getResource();
                }
                if (theContext instanceof org.hl7.fhir.r5.model.Reference) {
                    return ((org.hl7.fhir.r5.model.Reference) theContext).getResource();
                }
                return null;
            }
        });
        return fhirPath;
    }
}
