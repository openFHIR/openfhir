package com.syntaric.openfhir.util;

import com.syntaric.openfhir.aql.FhirQueryParam;
import com.syntaric.openfhir.fc.FhirConnectConst;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OpenFhirMapperUtils {

    final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss");
    final SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd");

    public String replaceAqlSuffixWithFlatSuffix(final String pathWithAqlSuffix,
                                                 final String detectedType) {
        if (pathWithAqlSuffix == null) {
            return "";
        }
        if (!endsWithAqlSuffix(pathWithAqlSuffix, detectedType)) {
            return "";
        }
        final String[] paths = pathWithAqlSuffix.split("/");
        final List<String> pathsAsList = Arrays.asList(paths);
        final String aqlSuffix = pathsAsList.get(pathsAsList.size() - 1);
        switch (aqlSuffix) {
            case "defining_code":
            case "|defining_code":
            case "code_string":
            case "|code_string":
                return "|" + FhirConnectConst.OPENEHR_CODE;
            case "terminology_id":
            case "|terminology_id":
            case "terminology_id/value":
                return "|" + FhirConnectConst.OPENEHR_TERMINOLOGY;
            case "function":
            case "|function":
                return "|function";
            case "value":
                return "|" + FhirConnectConst.OPENEHR_VALUE;
        }
        return "";
    }

    public boolean endsWithAqlSuffix(final String path,
                                     final String detectedType) {
        if (path == null) {
            return false;
        }
        final Set<String> relevantRmTypes = Set.of(FhirConnectConst.DV_CODED_TEXT,
                                                   FhirConnectConst.DV_PARTICIPATION,
                                                   FhirConnectConst.CODE_PHRASE);
        if (detectedType == null || !relevantRmTypes.contains(detectedType)) {
            return false;
        }
        if (path.endsWith("coded_text_value")) {
            // false positive
            return false;
        }
        return path.endsWith("defining_code")
                || path.endsWith("code_string")
                || path.endsWith("value")
                || path.endsWith("terminology_id")
                || path.endsWith("function")
                || path.endsWith("terminology_id/value")
                || path.endsWith("defining_code/code_string");
    }

    public String removeAqlSuffix(final String path,
                                  final String detectedType) {
        if (!endsWithAqlSuffix(path, detectedType)) {
            return path;
        }
        final String[] paths = path.split("/");
        final List<String> pathsAsList = Arrays.asList(paths);
        final String removed = pathsAsList.subList(0, pathsAsList.size() - 1).stream().collect(
                Collectors.joining("/"));
        return removeAqlSuffix(removed, detectedType);
    }

    /**
     * Parses the FHIR resource type from a URL such as:
     * http://something.com/fhir/Observation?code=123 → "Observation"
     * Observation?code=123 → "Observation"
     * Patient/$summary?something=param → "Patient"
     * Patient/123/$summary → "Patient"
     */
    public String parseFhirResourceType(final String fhirFullUrl) {
        if (StringUtils.isBlank(fhirFullUrl)) {
            throw new IllegalArgumentException("fhirFullUrl must not be blank");
        }
        final int questionMark = fhirFullUrl.indexOf('?');
        final String path = questionMark >= 0 ? fhirFullUrl.substring(0, questionMark) : fhirFullUrl;
        final String[] segments = path.split("/");
        for (final String segment : segments) {
            if (!segment.isEmpty() && Character.isUpperCase(segment.charAt(0))) {
                return segment;
            }
        }
        return segments[segments.length - 1];
    }

    /**
     * Parses query parameters from a FHIR URL into a list of FhirQueryParam.
     * e.g. Observation?code=123&amp;category=456 → [FhirQueryParam(code,123), FhirQueryParam(category,456)]
     */
    public List<FhirQueryParam> parseFhirQueryParams(final String fhirFullUrl) {
        final List<FhirQueryParam> params = new ArrayList<>();
        if (StringUtils.isBlank(fhirFullUrl)) {
            return params;
        }
        final int questionMark = fhirFullUrl.indexOf('?');
        final String pathPart = questionMark >= 0 ? fhirFullUrl.substring(0, questionMark) : fhirFullUrl;
        final int lastSlash = pathPart.lastIndexOf('/');
        final String lastSegment = lastSlash >= 0 ? pathPart.substring(lastSlash + 1) : pathPart;
        if (lastSegment.startsWith("$")) {
            params.add(new FhirQueryParam(null, null, lastSegment));
        }
        if (questionMark < 0 || questionMark == fhirFullUrl.length() - 1) {
            return params;
        }
        final String queryString = fhirFullUrl.substring(questionMark + 1);
        for (final String pair : queryString.split("&")) {
            final int eq = pair.indexOf('=');
            if (eq > 0) {
                params.add(new FhirQueryParam(pair.substring(0, eq), pair.substring(eq + 1), null));
            }
        }
        return params;
    }

    public String dateToString(final Date date) {
        if (date == null) {
            return null;
        }
        sdf2.setTimeZone(java.util.TimeZone.getDefault());
        return sdf2.format(date);
    }

    public String dateTimeToString(final Date date) {
        if (date == null) {
            return null;
        }
        sdf.setTimeZone(java.util.TimeZone.getDefault());
        return sdf.format(date);
    }

    public String timeToString(final Date date) {
        if (date == null) {
            return null;
        }
        time.setTimeZone(java.util.TimeZone.getDefault());
        return time.format(date);
    }

    public Date stringToDate(final String date) {
        if (date == null) {
            return null;
        }
        try {
            sdf.setTimeZone(java.util.TimeZone.getDefault());
            return sdf.parse(date);
        } catch (ParseException e) {
            log.error("Couldn't parse date: {}", date, e);
            try {
                sdf2.setTimeZone(java.util.TimeZone.getDefault());
                return sdf2.parse(date);
            } catch (ParseException ex) {
                log.error("Couldn't parse date: {}", date, e);
            }
        }
        return null;
    }
}
