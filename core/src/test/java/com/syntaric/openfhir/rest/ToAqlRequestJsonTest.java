package com.syntaric.openfhir.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntaric.openfhir.aql.ToAqlRequest;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * The body of {@code POST /openfhir/toaql}, read with an ObjectMapper built the way Spring builds the one the
 * controllers bind {@code @RequestBody} with ({@link Jackson2ObjectMapperBuilder}, which Spring Boot's
 * auto-configuration uses), and with a plain one, so the request does not depend on Spring's modules either. No
 * application context is started: Spring Boot's own customizers are not applied.
 */
public class ToAqlRequestJsonTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    public void readsASubject() throws Exception {
        final ToAqlRequest request = objectMapper.readValue(
                "{\"template\": \"EPS Patient Summary\", \"fhirFullUrl\": \"AllergyIntolerance\", "
                        + "\"subject\": {\"id\": \"194303193454\", \"namespace\": \"1.2.752.129.2.1.3.1\"}}",
                ToAqlRequest.class);

        Assert.assertEquals(new ToAqlRequest("EPS Patient Summary", null, "AllergyIntolerance",
                                             new ToAqlRequest.Subject("194303193454", "1.2.752.129.2.1.3.1")),
                            request);
    }

    @Test
    public void readsASubjectWithoutNamespace() throws Exception {
        final ToAqlRequest request = objectMapper.readValue(
                "{\"fhirFullUrl\": \"Condition\", \"subject\": {\"id\": \"194303193454\"}}", ToAqlRequest.class);

        Assert.assertEquals(new ToAqlRequest.Subject("194303193454", null), request.getSubject());
    }

    @Test
    public void readsASubjectWithAPlainObjectMapper() throws Exception {
        final ToAqlRequest request = new ObjectMapper().readValue(
                "{\"fhirFullUrl\": \"Condition\", \"subject\": {\"id\": \"1\", \"namespace\": \"n\"}}",
                ToAqlRequest.class);

        Assert.assertEquals(new ToAqlRequest(null, null, "Condition", new ToAqlRequest.Subject("1", "n")), request);
    }

    @Test
    public void readsAnEhrIdAsBefore() throws Exception {
        final ToAqlRequest request = objectMapper.readValue(
                "{\"template\": \"t\", \"ehrId\": \"7d44b88c\", \"fhirFullUrl\": \"Condition\"}", ToAqlRequest.class);

        Assert.assertEquals(new ToAqlRequest("t", "7d44b88c", "Condition"), request);
        Assert.assertNull(request.getSubject());
    }
}
