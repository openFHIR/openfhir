package com.syntaric.openfhir.mapping.tofhir;

import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.util.FhirInstanceCreator;
import com.syntaric.openfhir.util.FhirInstanceCreator.InstantiateAndSetReturn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ToFhirInstantiator {

    final private FhirInstanceCreator fhirInstanceCreator;

    @Autowired
    public ToFhirInstantiator(final FhirInstanceCreator fhirInstanceCreator) {
        this.fhirInstanceCreator = fhirInstanceCreator;
    }

    public Object instantiateElement(final MappingHelper mappingHelper,
                                      final String forcingClass,
                                      final int index,
                                      final String modelPackage) {
        return instantiateElement(mappingHelper, forcingClass, mappingHelper.getResolveResourceType(), index,
                                  modelPackage);
    }

    Object instantiateElement(final MappingHelper mappingHelper,
                              final String forcingClass,
                              final String resolveResourceType,
                              final int index,
                              final String modelPackage) {
        if (mappingHelper.isUseParentRoot()) {
            return propagateAndReturn(mappingHelper, resolveFhirBase(mappingHelper));
        }

        final Object fhirBase = resolveFhirBase(mappingHelper);
        if (fhirBase == null) {
            return propagateAndReturn(mappingHelper, null);
        }

        final String remainingPath = resolveRemainingPath(mappingHelper);
        final Object instantiated = resolveFromBase(fhirBase, remainingPath, forcingClass, resolveResourceType,
                                                    modelPackage);
        return propagateAndReturn(mappingHelper, instantiated);
    }

    /**
     * Determines the FHIR object to start navigation from, based on whether the mapping
     * path starts from the resource root or the current generating root.
     */
    private Object resolveFhirBase(final MappingHelper mappingHelper) {
        final boolean onResource = mappingHelper.getOriginalFhirPath()
                .startsWith(FhirConnectConst.FHIR_RESOURCE_FC);
        return onResource && !mappingHelper.isEnteredFromSlotArchetypeLink()
                ? mappingHelper.getGeneratingFhirResource()
                : mappingHelper.getGeneratingFhirRoot();
    }

    /**
     * Strips the FhirConnect path constant prefix to obtain the navigable remainder.
     */
    private String resolveRemainingPath(final MappingHelper mappingHelper) {
        final boolean onResource = mappingHelper.getOriginalFhirPath()
                .startsWith(FhirConnectConst.FHIR_RESOURCE_FC);
        return onResource
                ? mappingHelper.getFhir().replace(FhirConnectConst.FHIR_RESOURCE_FC + ".", "")
                : mappingHelper.getFhir()
                        .replace(FhirConnectConst.FHIR_ROOT_FC + ".", "")
                        .replace(FhirConnectConst.FHIR_ROOT_FC, "");
    }

    /**
     * Resolves the target element from the given base, handling both List and single-object bases.
     * Returns null if instantiation fails.
     */
    private Object resolveFromBase(final Object fhirBase,
                                   final String remainingPath,
                                   final String forcingClass,
                                   final String resolveResourceType,
                                   final String modelPackage) {
        if (fhirBase instanceof List<?> fhirBaseList) {
            return resolveFromList(fhirBaseList, remainingPath, forcingClass, resolveResourceType, modelPackage);
        }
        return resolveFromSingle(fhirBase, remainingPath, forcingClass, resolveResourceType, modelPackage);
    }

    /**
     * Resolves the target element from a List base, always against the element the current iteration is
     * building — the last one.
     *
     * <p>The openEHR occurrence index of the data point being mapped (e.g. the {@code 0} in
     * {@code prefix:0}) addresses a position inside the source cluster, not a position in this FHIR list.
     * The two only coincide when the list was built one-to-one from that same cluster. When one FHIR list
     * is fed by several mappings — e.g. a Patient's {@code name} receiving both an official name and a
     * maiden name from two instances of CLUSTER.structured_name.v1 — each mapping's occurrence index
     * restarts at 0, so using it here would reach back into an earlier mapping's entry and overwrite it.
     */
    private Object resolveFromList(final List<?> fhirBaseList,
                                   final String remainingPath,
                                   final String forcingClass,
                                   final String resolveResourceType,
                                   final String modelPackage) {
        final Object listEntry = fhirBaseList.get(fhirBaseList.size() - 1);
        return resolveFromSingle(listEntry, remainingPath, forcingClass, resolveResourceType, modelPackage);
    }

    /**
     * Resolves the target element from a single (non-List) base object.
     * Returns null and logs an error if instantiation fails.
     */
    private Object resolveFromSingle(final Object base,
                                     final String remainingPath,
                                     final String forcingClass,
                                     final String resolveResourceType,
                                     final String modelPackage) {
        final InstantiateAndSetReturn result =
                findExistingAndInstantiateRemainder(base, remainingPath, forcingClass, resolveResourceType, modelPackage);
        if (result == null) {
            log.error("Could not instantiate element based on fhirpath '{}' on {}",
                      remainingPath, base.getClass().getSimpleName());
            return null;
        }
        return getLastReturn(result).getReturning();
    }

    /**
     * Sets the instantiated element on the mapping helper and propagates it as the
     * generating root to all child helpers. Returns the element for convenience.
     */
    private Object propagateAndReturn(final MappingHelper mappingHelper, final Object instantiated) {
        mappingHelper.setGeneratingFhirBase(instantiated);
        mappingHelper.getChildren().forEach(child -> child.setGeneratingFhirRoot(instantiated));
        return instantiated;
    }

    /**
     * Returns the inner-most element in the InstantiateAndSetReturn chain,
     * since that's the one we need to populate.
     */
    public FhirInstanceCreator.InstantiateAndSetReturn getLastReturn(
            final FhirInstanceCreator.InstantiateAndSetReturn instantiateAndSetReturn) {
        if (instantiateAndSetReturn.getInner() == null) {
            return instantiateAndSetReturn;
        }
        return getLastReturn(instantiateAndSetReturn.getInner());
    }

    private InstantiateAndSetReturn findExistingAndInstantiateRemainder(final Object resource,
                                                                        final String remainingPath,
                                                                        final String forcingClass,
                                                                        final String resolveResourceType,
                                                                        final String modelPackage) {
        if (resource.getClass().getSimpleName().equals(remainingPath)) {
            return new InstantiateAndSetReturn(resource, false, null, "");
        }
        return fhirInstanceCreator.instantiateAndSetElement(
                resource, resource.getClass(), remainingPath, forcingClass, resolveResourceType, modelPackage);
    }
}
