package com.syntaric.openfhir.fc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.syntaric.openfhir.fc.schema.model.Condition;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * Documents persisted before the plural condition-field migration still carry the singular
 * {@code targetAttribute} / {@code criteria} keys. The Jackson {@code @JsonSetter} normalization
 * only runs when a mapping arrives over the REST API — the persistence round trips (Gson for the
 * Postgres entity, Spring Data field binding for Mongo) bind raw fields and bypass it. Those
 * objects used to surface {@code null} plural lists: the condition evaluator silently skipped them
 * (dropping the condition's semantics) and helper creation NPE'd on
 * {@code getTargetAttributes().iterator()}.
 * <p>
 * The getters now normalize the legacy singular fields lazily, so every materialization path is
 * covered at once.
 */
public class ConditionLegacySingularFieldsTest {

    private static final String LEGACY_JSON =
            "{\"targetRoot\":\"$resource.identifier\","
            + "\"targetAttribute\":\"system\","
            + "\"operator\":\"one of\","
            + "\"criteria\":\"http://example.org/sid/encounter-id\"}";

    /** The Postgres entity round trip: Gson binds by field name, no Jackson involved. */
    @Test
    public void gsonMaterializedLegacyDocumentNormalizesSingularFields() {
        final Condition condition = new Gson().fromJson(LEGACY_JSON, Condition.class);

        Assert.assertEquals(List.of("system"), condition.getTargetAttributes());
        Assert.assertEquals(List.of("http://example.org/sid/encounter-id"), condition.getCriterias());
    }

    /** copy() runs during helper creation on every request — it must carry the normalized values. */
    @Test
    public void copyOfLegacyConditionCarriesNormalizedValues() {
        final Condition copy = new Gson().fromJson(LEGACY_JSON, Condition.class).copy();

        Assert.assertEquals(List.of("system"), copy.getTargetAttributes());
        Assert.assertEquals(List.of("http://example.org/sid/encounter-id"), copy.getCriterias());
    }

    /** The plural form always wins when both are present (a legacy doc re-saved by newer code). */
    @Test
    public void pluralFormTakesPrecedenceOverLegacySingular() {
        final Condition condition = new Gson().fromJson(
                "{\"targetAttribute\":\"old\",\"targetAttributes\":[\"new\"],"
                + "\"criteria\":\"old\",\"criterias\":[\"new\"]}", Condition.class);

        Assert.assertEquals(List.of("new"), condition.getTargetAttributes());
        Assert.assertEquals(List.of("new"), condition.getCriterias());
    }

    /** A condition with neither form stays null — the evaluator treats that as a no-op. */
    @Test
    public void conditionWithoutAttributesStaysNull() {
        final Condition condition = new Gson().fromJson("{\"targetRoot\":\"$resource\"}", Condition.class);

        Assert.assertNull(condition.getTargetAttributes());
        Assert.assertNull(condition.getCriterias());
    }

    /** The Jackson path (YAML mappings over REST) keeps normalizing eagerly, unchanged. */
    @Test
    public void jacksonYamlWithSingularKeysStillNormalizes() throws Exception {
        final Condition condition = new ObjectMapper(new YAMLFactory()).readValue(
                "targetRoot: \"$resource.identifier\"\n"
                + "targetAttribute: \"system\"\n"
                + "operator: \"one of\"\n"
                + "criteria: \"http://example.org/sid/encounter-id\"\n", Condition.class);

        Assert.assertEquals(List.of("system"), condition.getTargetAttributes());
        Assert.assertEquals(List.of("http://example.org/sid/encounter-id"), condition.getCriterias());
    }

    /** The singular fields stay out of Jackson serialization — REST responses keep the plural shape. */
    @Test
    public void singularFieldsDoNotLeakIntoJacksonSerialization() throws Exception {
        final Condition condition = new Gson().fromJson(LEGACY_JSON, Condition.class);
        condition.getTargetAttributes(); // trigger normalization

        final String serialized = new ObjectMapper().writeValueAsString(condition);
        Assert.assertTrue(serialized.contains("targetAttributes"));
        Assert.assertFalse(serialized.contains("\"targetAttribute\""));
        Assert.assertFalse(serialized.contains("\"criteria\""));
    }
}
