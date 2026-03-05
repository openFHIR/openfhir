package com.syntaric.openfhir.mapping;

import static com.syntaric.openfhir.util.OpenFhirStringUtils.RESOLVE;

import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.model.Condition;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.utils.FHIRPathUtilityClasses.ClassTypeInfo;

@Slf4j
public class BidirectionalMappingEngine {

    private final FhirPathR4 fhirPath;

    protected BidirectionalMappingEngine(final FhirPathR4 fhirPath) {
        this.fhirPath = fhirPath;
    }

    /**
     * Returns false (and logs the reason) if the mapping should be skipped due to
     * unidirectional constraints or failing FHIR/openEHR type conditions.
     *
     * @param mappingDirection the direction this engine is executing toward —
     *         use {@link FhirConnectConst#UNIDIRECTIONAL_TOFHIR} or
     *         {@link FhirConnectConst#UNIDIRECTIONAL_TOOPENEHR}
     */
    protected boolean shouldProcessMapping(final MappingHelper mappingHelper,
                                           final String mappingDirection) {
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
        final Base generatingFhirResource = mappingHelper.getGeneratingFhirResource();
        if (!fhirTypePasses(mappingHelper, mappingHelper.getFhirConditions())) {
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
                                   final List<Condition> fhirConditions) {
        if (fhirConditions == null || fhirConditions.isEmpty()) {
            return true;
        }
        return fhirConditions.stream().allMatch(fhirCondition -> {
            if (!fhirCondition.getOperator().equals(FhirConnectConst.CONDITION_OPERATOR_TYPE)) {
                return true;
            }

            final String targetRoot = fhirCondition.getTargetRoot();
            final Base instance = mappingHelper.getGeneratingFhirResource();
            final String fhirPathType = String.format("%s[0].type()", targetRoot);

            if (fhirPathType.contains(RESOLVE)) {
                final String fhirPathAfterResolve = fhirPathType.split(".resolve\\(\\)")[1].substring(1);
                final Resource resolvedInstance = getReferencedResource((Resource) instance, fhirPathType);
                final Optional<ClassTypeInfo> isCorrectType = fhirPath.evaluateFirst(resolvedInstance,
                                                                                     fhirPathAfterResolve,
                                                                                     ClassTypeInfo.class);
                return isCorrectType.map(cr -> ((StringType) cr.getProperty(0, "name", false)[0]).getValue()
                                .equals(fhirCondition.getCriteria()))
                        .orElse(true);
            } else {
                final Optional<ClassTypeInfo> isCorrectType = fhirPath.evaluateFirst(instance, fhirPathType,
                                                                                     ClassTypeInfo.class);
                return isCorrectType.map(cr -> ((StringType) cr.getProperty(0, "name", false)[0]).getValue()
                                .equals(fhirCondition.getCriteria()))
                        .orElse(true);
            }
        });
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
            return condition.getCriterias().stream().anyMatch(c -> mappingHelper.getDetectedType().equals(c));
        });
    }

    private Resource getReferencedResource(final Resource initialResource, final String fhirPathExpr) {
        if (!fhirPathExpr.contains(RESOLVE)) {
            return initialResource;
        }
        final String fhirPathWithoutResolve = fhirPathExpr.split(RESOLVE)[0];
        try {
            final Reference reference = (Reference) fhirPath.evaluateFirst(initialResource,
                                                                           fhirPathWithoutResolve,
                                                                           Reference.class).get().getResource();
            return (Resource) reference.getResource();
        } catch (Exception e) {
            log.warn("Nothing resolved by evaluating {}", fhirPathWithoutResolve);
            return initialResource;
        }
    }
}
