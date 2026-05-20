package com.syntaric.openfhir.mapping.custommappings;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.util.BundleBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.mapping.tofhir.ToFhirInstantiator;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import com.syntaric.openfhir.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Custom mapping that generates a FHIR narrative (text) on the resource being built when mapping
 * openEHR→FHIR. Uses HAPI's built-in Thymeleaf narrative generator.
 */
@Slf4j
@Component
public class FeederAuditCustomMapping extends CustomMapping {

    private static final Set<String> CODES = Set.of("feederAudit");

    private final FhirContextRegistry fhirContextRegistry;
    private final ToFhirInstantiator toFhirInstantiator;

    @Autowired
    public FeederAuditCustomMapping(final FhirContextRegistry fhirContextRegistry,
                                    final ToFhirInstantiator toFhirInstantiator) {
        this.fhirContextRegistry = fhirContextRegistry;
        this.toFhirInstantiator = toFhirInstantiator;
    }

    public FeederAuditCustomMapping() {
        this.fhirContextRegistry = new FhirContextRegistry();
        this.toFhirInstantiator = new ToFhirInstantiator(new FhirInstanceCreator(new OpenFhirStringUtils(),
                new FhirInstanceCreatorUtility(new OpenFhirStringUtils())));
    }

    @Override
    public Set<String> mappingCodes() {
        return CODES;
    }

    /**
     * "blood_pressure/blood_pressure/_feeder_audit/originating_system_audit|system_id" : "system id",
     * "blood_pressure/blood_pressure/_feeder_audit/original_content|formalism" : "plain/text",
     * "blood_pressure/blood_pressure/_feeder_audit/original_content" : "something",
     * "blood_pressure/blood_pressure/_feeder_audit/originating_system_item_id:0|id" : "123",
     * "blood_pressure/blood_pressure/_feeder_audit/feeder_system_item_id:0|id" : "123",
     * <p>
     * "blood_pressure/_feeder_audit/original_content" : "something",
     * "blood_pressure/_feeder_audit/original_content|formalism" : "plain/text",
     * "blood_pressure/_feeder_audit/feeder_system_item_id:0|id" : "123",
     * "blood_pressure/_feeder_audit/originating_system_audit|system_id" : "system id",
     * "blood_pressure/_feeder_audit/originating_system_item_id:0|id" : "123"
     *
     */
    @Override
    public boolean applyFhirToOpenEhrMapping(final MappingHelper mappingHelper,
                                             final IBase fhirValue,
                                             final List<String> possibleRmTypes,
                                             final JsonObject flat,
                                             final OpenEhrPopulator populator,
                                             final OpenFhirMapperUtils mapperUtils,
                                             final OpenFhirStringUtils stringUtils) {
        final String originatingSystemId = extractArgument(mappingHelper.getProgrammedMapping());

        final Spec.Version version = detectVersion(mappingHelper.getGeneratingFhirResource());

        final List<IBase> toGenerateFeederAuditFrom = getResourceToGenerateFeederAuditFrom(mappingHelper, mappingHelper.getOriginalFhirPath(), version);

        if (toGenerateFeederAuditFrom == null || toGenerateFeederAuditFrom.isEmpty()) {
            log.warn("feederAudit invoked but fhirPath {} didn't yield any results", mappingHelper.getOriginalFhirPath());
            return false;
        }
        final FhirContext context = fhirContextRegistry.getContext(version);
        if (toGenerateFeederAuditFrom.size() == 1) {
            final IBase iBase = toGenerateFeederAuditFrom.get(0);
            flat.addProperty(getFeederAuditBasePath(mappingHelper.getFullOpenEhrFlatPath()) + "/original_content", context.newJsonParser().encodeToString(iBase));
        } else {
            final boolean allAreResources = toGenerateFeederAuditFrom.stream().allMatch(b -> b instanceof IBaseResource);
            if (allAreResources) {
                final BundleBuilder bb = new BundleBuilder(context);
                toGenerateFeederAuditFrom.forEach(r -> bb.addCollectionEntry((IBaseResource) r));
                flat.addProperty(getFeederAuditBasePath(mappingHelper.getFullOpenEhrFlatPath()) + "/original_content", context.newJsonParser().encodeResourceToString(bb.getBundle()));
            } else {
                flat.addProperty(getFeederAuditBasePath(mappingHelper.getFullOpenEhrFlatPath()) + "/original_content", new Gson().toJson(toGenerateFeederAuditFrom));
            }
        }
        flat.addProperty(getFeederAuditBasePath(mappingHelper.getFullOpenEhrFlatPath()) + "/original_content|formalism", "application/fhir+json");
        flat.addProperty(getFeederAuditBasePath(mappingHelper.getFullOpenEhrFlatPath()) + "/originating_system_audit|system_id", StringUtils.isEmpty(originatingSystemId) ? "openFHIR" : originatingSystemId); // todo: should come from somewhere

        return true;
    }

    private String getFeederAuditBasePath(final String fullOpenEhrPath) {
        final String withProperUnderscoreFeederAudit = fullOpenEhrPath.replace("feeder_audit", "_feeder_audit");
        return withProperUnderscoreFeederAudit.endsWith("_feeder_audit") ? withProperUnderscoreFeederAudit : (fullOpenEhrPath + "/_feeder_audit");
    }

    List<IBase> getResourceToGenerateFeederAuditFrom(final MappingHelper mappingHelper,
                                                     final String fhirPathForNarrativeGeneration,
                                                     final Spec.Version version) {
        if (fhirPathForNarrativeGeneration.equals(FhirConnectConst.FHIR_RESOURCE_FC)) {
            return Collections.singletonList((IBaseResource) mappingHelper.getGeneratingFhirResource());
        }
        if (fhirPathForNarrativeGeneration.equals(FhirConnectConst.FHIR_ROOT_FC)) {
            return Collections.singletonList((IBaseResource) mappingHelper.getGeneratingFhirRoot());
        }
        final IFhirPath fhirPath = fhirContextRegistry.getFhirPath(version);
        boolean fhirPathOnResource = fhirPathForNarrativeGeneration.startsWith(FhirConnectConst.FHIR_RESOURCE_FC);
        final Object generatingFhirRoot = fhirPathOnResource ?
                mappingHelper.getGeneratingFhirResource() : mappingHelper.getGeneratingFhirRoot();

        final String fhirPathForEvaluation = fhirPathOnResource ? fhirPathForNarrativeGeneration.replace(String.format("%s.",
                FhirConnectConst.FHIR_RESOURCE_FC), "") : fhirPathForNarrativeGeneration;

        final List<IBase> toReturn = new ArrayList<>();

        if (generatingFhirRoot instanceof List listOfGeneratedResources) {
            for (Object listOfGeneratedResource : listOfGeneratedResources) {
                final List<IBase> evaluated = fhirPath.evaluate((IBase) listOfGeneratedResource, fhirPathForEvaluation, IBase.class);
                evaluated.forEach(ev -> {
                    List<? extends IBase> iBases = resolveReference(ev, mappingHelper, fhirPath);
                    toReturn.addAll(iBases);
                });
            }
        } else {
            final List<IBase> evaluated = fhirPath.evaluate((IBase) generatingFhirRoot, fhirPathForEvaluation, IBase.class);
            if (evaluated.size() == 1) {
                return Collections.singletonList(resolveReference(evaluated.get(0), mappingHelper, fhirPath).get(0));
            }
            evaluated.forEach(ev -> {
                List<? extends IBase> iBases = resolveReference(ev, mappingHelper, fhirPath);
                toReturn.addAll(iBases);
            });
        }
        return toReturn;
    }

    private List<? extends IBase> resolveReference(final IBase toResolveOn,
                                                   final MappingHelper mappingHelper,
                                                   final IFhirPath versionedFhirPath) {
        if ("BundleEntryComponent".equals(toResolveOn.getClass().getSimpleName())
                || toResolveOn instanceof IBaseReference) {
            try {
                final Object resource = toResolveOn.getClass().getMethod("getResource").invoke(toResolveOn);
                return resource instanceof IBase ? List.of((IBaseResource) resource) : Collections.emptyList();
            } catch (final Exception e) {
                log.error("Could not get resource from BundleEntryComponent", e);
                return Collections.emptyList();
            }
        }

        if (FhirConnectConst.REFERENCE.equals(mappingHelper.getOriginalOpenEhrPath())) {
            return versionedFhirPath.evaluate(toResolveOn, "resolve()", IBaseResource.class);
        }
        if (toResolveOn instanceof IBaseResource) {
            return List.of((IBaseResource) toResolveOn);
        }
        return Collections.singletonList(toResolveOn);
    }

    private Spec.Version detectVersion(final IBase resource) {
        if (resource instanceof org.hl7.fhir.dstu3.model.Resource) {
            return Spec.Version.STU3;
        }
        if (resource instanceof org.hl7.fhir.r4b.model.Resource) {
            return Spec.Version.R4B;
        }
        if (resource instanceof org.hl7.fhir.r5.model.Resource) {
            return Spec.Version.R5;
        }
        return Spec.Version.R4;
    }
}
