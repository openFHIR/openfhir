package com.syntaric.openfhir.mapping.helpers.parser;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FhirValueReaders {

    private final OpenFhirMapperUtils mapperUtils;

    @Autowired
    public FhirValueReaders(OpenFhirMapperUtils mapperUtils) {
        this.mapperUtils = Objects.requireNonNull(mapperUtils);
    }

    public String basePath(String path) { //rewrite remove
        if (path == null) return null;
        int pipe = path.indexOf('|');
        return pipe >= 0 ? path.substring(0, pipe) : path;
    }

    public String get(JsonObject valueHolder, String path) {
        if (valueHolder != null && path != null && valueHolder.has(path)) {
            return valueHolder.get(path).getAsString();
        }
        return null;
    }

    public String get(JsonObject valueHolder, String basePath, String suffix, String fallbackPath) {
        String v = get(valueHolder, basePath + "|" + suffix);
        if (v != null) return v;
        return fallbackPath == null ? null : get(valueHolder, fallbackPath);
    }

    public Object number(String value) {
        if (StringUtils.isEmpty(value)) return null;
        try { return Long.parseLong(value); }
        catch (Exception e) { return Double.parseDouble(value); }
    }

    public java.util.Date date(String s) {
        return mapperUtils.stringToDate(s);
    }

    public String cleanVersionFromSystem(String system) {
        if (system == null) return null;
        int open = system.lastIndexOf('(');
        int close = system.lastIndexOf(')');
        if (open >= 0 && close > open) return system.substring(0, open).trim();
        return system;
    }

    public String version(String terminologyRaw) {
        if (terminologyRaw == null) return null;
        int open = terminologyRaw.indexOf('(');
        int close = terminologyRaw.indexOf(')');
        if (open >= 0 && close > open) return terminologyRaw.substring(open + 1, close);
        return null;
    }

    public boolean mappingExists(JsonObject valueHolder, String mappingPrefix) {
        for (String key : valueHolder.keySet()) {
            if (key.startsWith(mappingPrefix)) return true;
        }
        return false;
    }
}
