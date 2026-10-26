package com.syntaric.openfhir.util;

import com.syntaric.openfhir.aql.FhirQueryParam;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OpenFhirMapperUtils {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String TIME_PATTERN = "HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    /**
     * A fresh formatter per call, in the JVM's default time zone: {@link SimpleDateFormat} is not thread-safe and
     * this bean is shared by every request thread. Kept as {@code SimpleDateFormat} (rather than java.time) so
     * parsing stays exactly as lenient as before.
     */
    private static SimpleDateFormat formatter(final String pattern) {
        return new SimpleDateFormat(pattern);
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
        return formatter(DATE_PATTERN).format(date);
    }

    public String dateTimeToString(final Date date) {
        if (date == null) {
            return null;
        }
        return formatter(DATE_TIME_PATTERN).format(date);
    }

    public String timeToString(final Date date) {
        if (date == null) {
            return null;
        }
        return formatter(TIME_PATTERN).format(date);
    }

    public Date stringToDate(final String date) {
        if (date == null) {
            return null;
        }
        try {
            return formatter(DATE_TIME_PATTERN).parse(date);
        } catch (ParseException e) {
            log.error("Couldn't parse date: {}", date, e);
            try {
                return formatter(DATE_PATTERN).parse(date);
            } catch (ParseException ex) {
                log.error("Couldn't parse date: {}", date, e);
            }
        }
        return null;
    }
}
