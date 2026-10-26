package com.syntaric.openfhir.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import java.io.IOException;
import java.util.List;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

class FhirConnectValidatorTest {

    @Test
    void validateAgainstModelSchema() throws IOException {
        final ObjectMapper yaml = OpenFhirTestUtility.getYaml();
        final FhirConnectModel fhirConnectModel = yaml.readValue(
                getClass().getResourceAsStream("/kds/core/projects/org.highmed/KDS/diagnose/KDS_problem_diagnose.yml"),
                FhirConnectModel.class);
        final List<String> strings = new FhirConnectValidator().validateAgainstModelSchema(fhirConnectModel);
        Assert.assertTrue(strings.isEmpty());
    }

    @Test
    void validateAgainstModelCondition() throws IOException {
        final ObjectMapper yaml = OpenFhirTestUtility.getYaml();
        final FhirConnectModel fhirConnectModel = yaml.readValue(
                getClass().getResourceAsStream("/growth_chart/body-height.model.yml"),
                FhirConnectModel.class);
        final List<String> strings = new FhirConnectValidator().validateAgainstModelSchema(fhirConnectModel);
        Assert.assertTrue(strings.isEmpty());
    }

    @Test
    void slotContext_isAccepted() throws IOException {
        final FhirConnectValidator validator = new FhirConnectValidator();
        final FhirConnectModel model = modelWith("    slotContext: \"a.context\"\n");

        Assert.assertTrue(validator.validateAgainstModelSchema(model).isEmpty());
        Assert.assertTrue(validator.validateFhirConnectModel(model).isEmpty());
    }

    @Test
    void slotContextAndSlotArchetype_areMutuallyExclusive() throws IOException {
        final FhirConnectValidator validator = new FhirConnectValidator();
        final FhirConnectModel model = modelWith(
                "    slotContext: \"a.context\"\n    slotArchetype: \"SECTION.adhoc.v1\"\n");

        Assert.assertFalse(validator.validateAgainstModelSchema(model).isEmpty());
        Assert.assertEquals(1, validator.validateFhirConnectModel(model).size());
    }

    @Test
    void slotContextWithOpenEhrReference_isRejected() throws IOException {
        final FhirConnectModel model = modelWith("    slotContext: \"a.context\"\n");
        model.getMappings().get(0).getWith().setOpenehr("$reference/items[openEHR-EHR-SECTION.adhoc.v1]");

        final List<String> errors = new FhirConnectValidator().validateFhirConnectModel(model);
        Assert.assertEquals(1, errors.size());
        Assert.assertTrue(errors.get(0).contains("separate composition"));
    }

    @Test
    void slotContextNotListedUnderContexts_isUndeclared() throws IOException {
        final FhirConnectValidator validator = new FhirConnectValidator();
        final FhirConnectModel model = modelWith("    slotContext: \"a.context\"\n");

        final List<String> errors = validator.validateSubContexts(context("root.context", null), List.of(),
                                                                  List.of(model));
        Assert.assertEquals(1, errors.size());
        Assert.assertTrue(errors.get(0).startsWith("Undeclared sub-context"));

        Assert.assertTrue(validator.validateSubContexts(context("root.context", "a.context"), List.of(),
                                                        List.of(model)).isEmpty());
    }

    @Test
    void cyclicContexts_areRejected() throws IOException {
        final List<String> errors = new FhirConnectValidator().validateSubContexts(
                context("a.context", "b.context"), List.of(context("b.context", "a.context")), List.of());

        Assert.assertEquals(1, errors.size());
        Assert.assertEquals("Cyclic context reference: a.context -> b.context -> a.context", errors.get(0));
    }

    private FhirConnectModel modelWith(final String slot) throws IOException {
        return OpenFhirTestUtility.getYaml().readValue(
                "grammar: FHIRConnect/v1.0.0\n"
                        + "type: model\n"
                        + "metadata:\n"
                        + "  name: COMPOSITION.health_summary.v1\n"
                        + "  version: 0.0.1-alpha\n"
                        + "spec:\n"
                        + "  system: FHIR\n"
                        + "  version: R4\n"
                        + "  openEhrConfig:\n"
                        + "    archetype: openEHR-EHR-COMPOSITION.health_summary.v1\n"
                        + "  fhirConfig:\n"
                        + "    structureDefinition: http://hl7.org/fhir/StructureDefinition/Composition\n"
                        + "mappings:\n"
                        + "  - name: \"section\"\n"
                        + "    with:\n"
                        + "      fhir: \"$resource.section\"\n"
                        + "      openehr: \"$composition/content[openEHR-EHR-SECTION.adhoc.v1]\"\n"
                        + slot,
                FhirConnectModel.class);
    }

    private FhirConnectContext context(final String name, final String subContext) throws IOException {
        return OpenFhirTestUtility.getYaml().readValue(
                "grammar: FHIRConnect/v1.0.0\n"
                        + "type: context\n"
                        + "metadata:\n"
                        + "  name: " + name + "\n"
                        + "  version: 0.0.1-alpha\n"
                        + "spec:\n"
                        + "  system: FHIR\n"
                        + "  version: R4\n"
                        + "context:\n"
                        + "  profile:\n"
                        + "    url: \"http://example.org/StructureDefinition/summary\"\n"
                        + "  template:\n"
                        + "    id: \"Summary\"\n"
                        + "  archetypes:\n"
                        + "    - \"COMPOSITION.health_summary.v1\"\n"
                        + (subContext == null ? "" : "  contexts:\n    - \"" + subContext + "\"\n")
                        + "  start: \"COMPOSITION.health_summary.v1\"\n",
                FhirConnectContext.class);
    }
}
