package com.syntaric.openfhir.mapping.toopenehr;

import static com.syntaric.openfhir.util.OpenFhirStringUtils.RESOLVE;
import static com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.util.OpenEhrPopulator;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Base;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ToOpenEhrNullFlavour {

    private final OpenFhirStringUtils openFhirStringUtils;
    private final OpenEhrPopulator openEhrPopulator;
    private final FhirPathR4 fhirPathR4;

    @Autowired
    public ToOpenEhrNullFlavour(final OpenFhirStringUtils openFhirStringUtils,
                                final OpenEhrPopulator openEhrPopulator,
                                final FhirPathR4 fhirPathR4) {
        this.openFhirStringUtils = openFhirStringUtils;
        this.openEhrPopulator = openEhrPopulator;
        this.fhirPathR4 = fhirPathR4;
    }

    public boolean handleDataAbsentReasonWhenNoResult(final MappingHelper helper,
                                                      final JsonObject flatComposition,
                                                      final Base toResolveOn) {
        if (helper == null || flatComposition == null || toResolveOn == null) {
            return false;
        }
        final String nullFlavourPath = deriveNullFlavourPath(helper.getFullOpenEhrFlatPath());
        if (StringUtils.isBlank(nullFlavourPath)) {
            return false;
        }
        final List<Base> dataAbsentReasons = resolveDataAbsentReasonValuesForMissingPath(
                toResolveOn, helper.getFhir());
        if (dataAbsentReasons.isEmpty()) {
            return false;
        }
        for (Base reason : dataAbsentReasons) {
            if (openEhrPopulator.setNullFlavourForDataAbsentReason(nullFlavourPath, reason, flatComposition)) {
                return true;
            }
        }
        return false;
    }

    private List<Base> resolveDataAbsentReasonValuesForMissingPath(final Base rootElement,
                                                                   final String originalFhirPath) {
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
            final List<Base> containers = evaluateContainers(rootElement, containerPath);
            for (Base container : containers) {
                final List<Base> values = resolveDataAbsentReasonValuesFromElement(container);
                if (values != null && !values.isEmpty()) {
                    return values;
                }
            }
        }

        // For non-value mappings, only honor DAR extension on the exact missing target element.
        return resolveDataAbsentReasonExtensionOnExactPath(rootElement, parts);
    }

    private List<Base> resolveDataAbsentReasonExtensionOnExactPath(final Base rootElement, final List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return Collections.emptyList();
        }
        final String leaf = parts.get(parts.size() - 1);
        if (StringUtils.isBlank(leaf) || isCastSegment(leaf) || RESOLVE.equals(leaf)) {
            return Collections.emptyList();
        }
        final String parentPath = parts.size() == 1 ? "" : String.join(".", parts.subList(0, parts.size() - 1));
        final List<Base> containers = evaluateContainers(rootElement, parentPath);
        if (containers.isEmpty()) {
            return Collections.emptyList();
        }
        for (Base container : containers) {
            try {
                final String extensionPath =
                        leaf + ".extension('" + OpenEhrPopulator.DATA_ABSENT_REASON_URL + "').value";
                final List<Base> extensionValues = fhirPathR4.evaluate(container, extensionPath, Base.class);
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

    private List<Base> resolveDataAbsentReasonValuesFromElement(final Base element) {
        if (element == null) {
            return Collections.emptyList();
        }
        try {
            final List<Base> extensionValues = fhirPathR4.evaluate(element,
                                                                   "extension('"
                                                                           + OpenEhrPopulator.DATA_ABSENT_REASON_URL
                                                                           + "').value",
                                                                   Base.class);
            if (extensionValues != null && !extensionValues.isEmpty()) {
                return extensionValues;
            }
        } catch (Exception e) {
            log.debug("Unable to evaluate data absent reason extension on element of type {}: {}",
                      element.getClass(), e.getMessage());
        }
        try {
            final List<Base> propertyValues = fhirPathR4.evaluate(element, "dataAbsentReason", Base.class);
            if (propertyValues != null && !propertyValues.isEmpty()) {
                return propertyValues;
            }
        } catch (Exception e) {
            log.debug("Unable to evaluate dataAbsentReason property on element of type {}: {}",
                      element.getClass(), e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<Base> evaluateContainers(final Base rootElement, final String containerPath) {
        try {
            if (StringUtils.isBlank(containerPath)) {
                return Collections.singletonList(rootElement);
            }
            final List<Base> evaluated = fhirPathR4.evaluate(rootElement, containerPath, Base.class);
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

    private String normalizeFhirPathForDataAbsentReason(final Base rootElement, final String originalFhirPath) {
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
