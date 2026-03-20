package com.syntaric.openfhir.aql;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ToAqlResponse {
    private List<AqlResponse> aqls;
    private List<UnhandledParam> unhandledParams;

    public ToAqlResponse addAql(final String aql,
                                final AqlType type) {
        if (aqls == null) {
            aqls = new ArrayList<>();
        }
        if (StringUtils.isNotEmpty(aql)) {
            aqls.add(new AqlResponse(aql, type));
        }
        return this;
    }

    public ToAqlResponse addAql(final AqlResponse response) {
        if (aqls == null) {
            aqls = new ArrayList<>();
        }
        if (response != null) {
            aqls.add(response);
        }
        return this;
    }

    public ToAqlResponse addAqls(final List<AqlResponse> responses) {
        if (aqls == null) {
            aqls = new ArrayList<>();
        }
        if (responses != null && !responses.isEmpty()) {
            aqls.addAll(responses);
        }
        return this;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AqlResponse {
        @Setter
        private String aql;
        private AqlType type;
    }

    public enum AqlType {
        COMPOSITION, ENTRY
    }

    public ToAqlResponse addUnhandledParam(final String paramName,
                                           final UnhandledParamType type,
                                           final String message) {
        if (unhandledParams == null) {
            unhandledParams = new ArrayList<>();
        }
        unhandledParams.add(new UnhandledParam(paramName, type, message));
        return this;
    }

    @Data
    public static class UnhandledParam {
        private final String paramName;
        private final UnhandledParamType type;
        private final String message;
    }

    public enum UnhandledParamType {
        WARNING, ERROR
    }
}
