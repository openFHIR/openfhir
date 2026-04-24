package com.syntaric.openfhir.mapping;

import static com.syntaric.openfhir.util.OpenFhirStringUtils.RESOLVE;

import ca.uhn.fhir.fhirpath.IFhirPath;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.producers.FhirContextRegistry;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.StringType;

@Slf4j
public class BidirectionalMappingEngine {

    private final FhirContextRegistry fhirContextRegistry;

    protected BidirectionalMappingEngine(final FhirContextRegistry fhirContextRegistry) {
        this.fhirContextRegistry = fhirContextRegistry;
    }

    /**
     * Returns false (and logs the reason) if the mapping should be skipped due to
     * unidirectional constraints or failing FHIR/openEHR type conditions.
     *
     * @param mappingDirection the direction this engine is executing toward —
     *                         use {@link FhirConnectConst#UNIDIRECTIONAL_TOFHIR} or
     *                         {@link FhirConnectConst#UNIDIRECTIONAL_TOOPENEHR}
     */
    protected boolean shouldProcessMapping(final MappingHelper mappingHelper,
                                           final String mappingDirection,
                                           final Spec.Version fhirVersion) {
        final boolean toFhir = FhirConnectConst.UNIDIRECTIONAL_TOFHIR.equals(mappingDirection);
        final String executionLog = toFhir ? "ToFhirExecution" : "ToOpenEhrExecution";
        final String blockedUnidirectional = toFhir
                ? FhirConnectConst.UNIDIRECTIONAL_TOOPENEHR
                : FhirConnectConst.UNIDIRECTIONAL_TOFHIR;

        if (blockedUnidirectional.equalsIgnoreCase(mappingHelper.getUnidirectional())) {
            log.info("[{}] Unidirectional '{}' for mapping name {}; skipping mapping.",
                    executionLog, blockedUnidirectional, mappingHelper.getMappingName());
            return false;
        }
        final IBase generatingFhirResource = mappingHelper.getGeneratingFhirResource();
        if (!fhirTypePasses(mappingHelper, mappingHelper.getFhirConditions(), fhirVersion)) {
            log.info("[{}] FHIR type '{}' does not pass conditions for mapping name {}; skipping mapping.",
                    executionLog, generatingFhirResource == null ? "NULL" : generatingFhirResource.fhirType(),
                    mappingHelper.getMappingName());
            return false;
        }
        if (!openEhrTypePasses(mappingHelper, mappingHelper.getOpenEhrConditions())) {
            log.info("[{}] OpenEHR type '{}' does not pass conditions for mapping name {}; skipping mapping.",
                    executionLog, generatingFhirResource == null ? "NULL" : generatingFhirResource.fhirType(),
                    mappingHelper.getMappingName());
            return false;
        }
        return true;
    }

    private boolean fhirTypePasses(final MappingHelper mappingHelper,
                                   final List<Condition> fhirConditions,
                                   final Spec.Version fhirVersion) {
        if (fhirConditions == null || fhirConditions.isEmpty()) {
            return true;
        }
        final IFhirPath fhirPath = fhirContextRegistry.getFhirPath(fhirVersion);
        return fhirConditions.stream().allMatch(fhirCondition -> {
            if (!fhirCondition.getOperator().equals(FhirConnectConst.CONDITION_OPERATOR_TYPE)) {
                return true;
            }

            final String targetRoot = fhirCondition.getTargetRoot();
            final boolean takeFhirRoot = targetRoot.equals(mappingHelper.getFullFhirPath());
            final IBase instance = takeFhirRoot ? (Base) mappingHelper.getGeneratingFhirRoot() : mappingHelper.getGeneratingFhirResource();
            final String fhirPathExpr = takeFhirRoot ? mappingHelper.getFhir() : targetRoot;

            if (fhirPathExpr.contains(RESOLVE)) {
                final IBase resolvedInstance = getReferencedResource(instance, fhirPathExpr, fhirPath);
                final String afterResolve = fhirPathExpr.split("\\.resolve\\(\\)")[1].substring(1);
                return resolvesFhirTypeName(resolvedInstance, afterResolve + "[0].type().name", fhirPath)
                        .map(name -> name.equals(fhirCondition.getCriteria()))
                        .orElse(true);
            } else {
                return resolvesFhirTypeName(instance, fhirPathExpr + "[0].type().name", fhirPath)
                        .map(name -> name.equals(fhirCondition.getCriteria()))
                        .orElse(true);
            }
        });
    }

    private Optional<String> resolvesFhirTypeName(final IBase instance, final String fhirPathExpr,
                                                   final IFhirPath fhirPath) {
        try {
            final Optional<IBase> result = fhirPath.evaluateFirst(instance, fhirPathExpr, IBase.class);
            return result.map(r -> {
                if (r instanceof StringType) {
                    return ((StringType) r).getValue();
                }
                return r.toString();
            });
        } catch (final Exception e) {
            log.debug("Could not evaluate type name via FHIRPath '{}': {}", fhirPathExpr, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean openEhrTypePasses(final MappingHelper mappingHelper,
                                      final List<Condition> openEhrConditions) {
        if (openEhrConditions == null || openEhrConditions.isEmpty()) {
            return true;
        }
        return openEhrConditions.stream().allMatch(condition -> {
            if (!condition.getOperator().equals(FhirConnectConst.CONDITION_OPERATOR_TYPE)) {
                return true;
            }
            if (mappingHelper.getDetectedType() != null) {
                // this should be populated when going openehr -> fhir, because in that case we know exactly which
                // type it is
                return condition.getCriterias().stream().anyMatch(c -> mappingHelper.getDetectedType().equals(c));
            } else {
                // this is fhir->openehr and we just check if possibleRmType is one of those
                return condition.getCriterias().stream().anyMatch(c -> mappingHelper.getPossibleRmTypes().contains(c));
            }
        });
    }

    private IBase getReferencedResource(final IBase initialResource, final String fhirPathExpr,
                                         final IFhirPath fhirPath) {
        if (!fhirPathExpr.contains(RESOLVE)) {
            return initialResource;
        }
        final String fhirPathWithoutResolve = fhirPathExpr.split(String.format("\\.%s", RESOLVE))[0];
        try {
            final Optional<IBase> reference = fhirPath.evaluateFirst(initialResource,
                    fhirPathWithoutResolve,
                    IBase.class);
            if (reference.isEmpty()) {
                return initialResource;
            }
            final IBase ref = reference.get();
            // Handle R4, R4B, R5 Reference types via reflection to stay version-agnostic
            try {
                final Object resource = ref.getClass().getMethod("getResource").invoke(ref);
                if (resource instanceof Base) {
                    return (Base) resource;
                }
            } catch (final Exception ignored) {
                // not a Reference or no getResource method
            }
            return initialResource;
        } catch (Exception e) {
            log.warn("Nothing resolved by evaluating {}", fhirPathWithoutResolve);
            return initialResource;
        }
    }
}
