package com.syntaric.openfhir.mapping.toopenehr;

import ca.uhn.fhir.fhirpath.IFhirPath;
import com.google.gson.JsonObject;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.util.OpenEhrPopulator;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX;
import static com.syntaric.openfhir.util.OpenFhirStringUtils.RESOLVE;

@Component
@Slf4j
public class ToOpenEhrNullFlavour {

    private final OpenFhirStringUtils openFhirStringUtils;
    private final OpenEhrPopulator openEhrPopulator;

    @Autowired
    public ToOpenEhrNullFlavour(final OpenFhirStringUtils openFhirStringUtils,
                                final OpenEhrPopulator openEhrPopulator) {
        this.openFhirStringUtils = openFhirStringUtils;
        this.openEhrPopulator = openEhrPopulator;
    }

    public boolean handleDataAbsentReasonWhenNoResult(final MappingHelper helper,
                                                      final JsonObject flatComposition,
                                                      final IBase toResolveOn,
                                                      final IFhirPath versionedFhirPath,
                                                      final Class<? extends IBase> baseClass) {
        if (helper == null || flatComposition == null || toResolveOn == null) {
            return false;
        }
        final String nullFlavourPath = deriveNullFlavourPath(helper.getFullOpenEhrFlatPath());
        if (StringUtils.isBlank(nullFlavourPath)) {
            return false;
        }
        final List<? extends IBase> dataAbsentReasons = resolveDataAbsentReasonValuesForMissingPath(
                toResolveOn, helper.getFhir(), versionedFhirPath, baseClass);
        if (dataAbsentReasons.isEmpty()) {
            return false;
        }
        for (IBase reason : dataAbsentReasons) {
            if (openEhrPopulator.setNullFlavourForDataAbsentReason(nullFlavourPath, reason, flatComposition)) {
                return true;
            }
        }
        return false;
    }

    private List<? extends IBase> resolveDataAbsentReasonValuesForMissingPath(final IBase rootElement,
                                                                    final String originalFhirPath,
                                                                    final IFhirPath versionedFhirPath,
                                                                    final Class<? extends IBase> baseClass) {
        if (rootElement == null || StringUtils.isBlank(originalFhirPath)) {
            return Collections.emptyList();
        }
        final String normalizedPath = normalizeFhirPathForDataAbsentReason(rootElement, originalFhirPath);
        if (StringUtils.isBlank(normalizedPath)) {
            return Collections.emptyList();
        }
        final List<String> parts = openFhirStringUtils.splitFhirPathTopLevel(normalizedPath).stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        if (parts.isEmpty()) {
            return Collections.emptyList();
        }

        final int valueIndex = findValueSegmentIndex(parts);
        if (valueIndex >= 0) {
            final String containerPath = valueIndex == 0 ? "" : String.join(".", parts.subList(0, valueIndex));
            final List<? extends IBase> containers = evaluateContainers(rootElement, containerPath, versionedFhirPath, baseClass);
            for (IBase container : containers) {
                final List<? extends IBase> values = resolveDataAbsentReasonValuesFromElement(container, versionedFhirPath, baseClass);
                if (values != null && !values.isEmpty()) {
                    return values;
                }
            }
        }

        // For non-value mappings, only honor DAR extension on the exact missing target element.
        return resolveDataAbsentReasonExtensionOnExactPath(rootElement, parts, versionedFhirPath, baseClass);
    }

    private List<? extends IBase> resolveDataAbsentReasonExtensionOnExactPath(final IBase rootElement,
                                                                     final List<String> parts,
                                                                     final IFhirPath versionedFhirPath,
                                                                     final Class<? extends IBase> baseClass) {
        if (parts == null || parts.isEmpty()) {
            return Collections.emptyList();
        }
        final String leaf = parts.get(parts.size() - 1);
        if (StringUtils.isBlank(leaf) || isCastSegment(leaf) || RESOLVE.equals(leaf)) {
            return Collections.emptyList();
        }
        final String parentPath = parts.size() == 1 ? "" : String.join(".", parts.subList(0, parts.size() - 1));
        final List<? extends IBase> containers = evaluateContainers(rootElement, parentPath, versionedFhirPath, baseClass);
        if (containers.isEmpty()) {
            return Collections.emptyList();
        }
        for (IBase container : containers) {
            try {
                final String extensionPath =
                        leaf + ".extension('" + OpenEhrPopulator.DATA_ABSENT_REASON_URL + "').value";
                final List<? extends IBase> extensionValues = versionedFhirPath.evaluate(container, extensionPath, baseClass);
                if (extensionValues != null && !extensionValues.isEmpty()) {
                    return extensionValues;
                }
            } catch (Exception e) {
                log.debug("Unable to evaluate data absent reason extension for path '{}' on element {}: {}",
                          leaf, container.getClass(), e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    private List<? extends IBase> resolveDataAbsentReasonValuesFromElement(final IBase element,
                                                                  final IFhirPath versionedFhirPath,
                                                                  final Class<? extends IBase> baseClass) {
        if (element == null) {
            return Collections.emptyList();
        }
        try {
            final List<? extends IBase> extensionValues = versionedFhirPath.evaluate(element,
                                                                            "extension('"
                                                                                    + OpenEhrPopulator.DATA_ABSENT_REASON_URL
                                                                                    + "').value",
                                                                            baseClass);
            if (extensionValues != null && !extensionValues.isEmpty()) {
                return extensionValues;
            }
        } catch (Exception e) {
            log.debug("Unable to evaluate data absent reason extension on element of type {}: {}",
                      element.getClass(), e.getMessage());
        }
        try {
            final List<? extends IBase> propertyValues = versionedFhirPath.evaluate(element, "dataAbsentReason", baseClass);
            if (propertyValues != null && !propertyValues.isEmpty()) {
                return propertyValues;
            }
        } catch (Exception e) {
            log.debug("Unable to evaluate dataAbsentReason property on element of type {}: {}",
                      element.getClass(), e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<? extends IBase> evaluateContainers(final IBase rootElement, final String containerPath,
                                            final IFhirPath versionedFhirPath,
                                            final Class<? extends IBase> baseClass) {
        try {
            if (StringUtils.isBlank(containerPath)) {
                return Collections.singletonList(rootElement);
            }
            final List<? extends IBase> evaluated = versionedFhirPath.evaluate(rootElement, containerPath, baseClass);
            return evaluated == null ? Collections.emptyList() : evaluated;
        } catch (Exception e) {
            log.debug("Unable to evaluate data absent reason container path '{}' on {}: {}",
                      containerPath, rootElement.getClass(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private int findValueSegmentIndex(final List<String> parts) {
        for (int i = parts.size() - 1; i >= 0; i--) {
            final String segment = parts.get(i);
            if (isCastSegment(segment) || RESOLVE.equals(segment)) {
                continue;
            }
            if (isValueLikeSegment(segment)) {
                return i;
            }
            return -1;
        }
        return -1;
    }

    private boolean isValueLikeSegment(final String segment) {
        return "value".equals(segment) || "value[x]".equals(segment) || segment.startsWith("value");
    }

    private boolean isCastSegment(final String segment) {
        return segment != null && segment.startsWith("as(") && segment.endsWith(")");
    }

    private String normalizeFhirPathForDataAbsentReason(final IBase rootElement, final String originalFhirPath) {
        String path = originalFhirPath;
        if (path.startsWith(".")) {
            path = path.substring(1);
        }
        path = openFhirStringUtils.fixFhirPathCasting(path);
        final List<String> parts = openFhirStringUtils.splitFhirPathTopLevel(path);
        if (!parts.isEmpty() && rootElement.fhirType().equals(parts.get(0))) {
            path = String.join(".", parts.subList(1, parts.size()));
        }
        return path;
    }

    private String deriveNullFlavourPath(final String openEhrPath) {
        if (StringUtils.isBlank(openEhrPath)) {
            return null;
        }
        String basePath = openEhrPath;
        final int pipeIndex = basePath.indexOf('|');
        if (pipeIndex >= 0) {
            basePath = basePath.substring(0, pipeIndex);
        }
        if (basePath.contains(RECURRING_SYNTAX)) {
            basePath = basePath.replace(RECURRING_SYNTAX, ":0");
        }
        basePath = stripTerminalValueNode(basePath);
        if (basePath.endsWith("_null_flavour")) {
            return basePath;
        }
        if (basePath.endsWith("/")) {
            return basePath + "_null_flavour";
        }
        return basePath + "/_null_flavour";
    }

    private String stripTerminalValueNode(final String basePath) {
        if (StringUtils.isBlank(basePath)) {
            return basePath;
        }
        final int lastSlash = basePath.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == basePath.length() - 1) {
            return basePath;
        }
        final String lastSegment = basePath.substring(lastSlash + 1);
        // openEHR flat value nodes like quantity_value/date_time_value/identifier_value
        // are not ELEMENT paths and can't carry null_flavour directly.
        if (lastSegment.endsWith("_value")) {
            return basePath.substring(0, lastSlash);
        }
        return basePath;
    }
}
