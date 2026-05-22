package com.syntaric.openfhir.mapping.allergies;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.GenericTest;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.dstu3.model.AllergyIntolerance;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Extension;
import org.junit.Assert;
import org.junit.Test;

public class AllergiesSTU3BidirectionalTest extends GenericTest {

    static final String RESOURCES = "/allergies/";
    static final String CONTEXT_MAPPING = RESOURCES + "context.yml";
    static final String OPT = "DataHub-AllergyIntolerance-2017.opt";
    static final String TEST_BUNDLE = RESOURCES + "zib-AllergyIntolerance-bundle.json";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(RESOURCES + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(RESOURCES).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void toFhirToOpenEhrToFhir() {
        // Round 1: FHIR -> openEHR
        final Bundle bundle = (Bundle) getStu3TestBundle(TEST_BUNDLE);
        final JsonObject flatJson = toOpenEhr.fhirToFlatJsonObject(context, bundle, webTemplate);

        assertFlatJson(flatJson);

        Composition canonicalComposition = new FlatJsonUnmarshaller().unmarshal(new Gson().toJson(flatJson), webTemplate);

        // Round 2: openEHR -> FHIR
        final Bundle fhirBundle = (Bundle) toFhir.compositionsToFhir(context, List.of(canonicalComposition), webTemplate);

        assertFhirBundle(fhirBundle);
    }

    private void assertFhirBundle(final Bundle fhirBundle) {
        Assert.assertNotNull("fhirBundle must not be null", fhirBundle);
        Assert.assertFalse("fhirBundle must have entries", fhirBundle.getEntry().isEmpty());

        final AllergyIntolerance allergy = (AllergyIntolerance) fhirBundle.getEntry().get(0).getResource();
        Assert.assertNotNull("AllergyIntolerance must not be null", allergy);

        // code: Bee venom (SNOMED 288328004)
        Assert.assertTrue("code must contain SNOMED coding 288328004",
                allergy.getCode().getCoding().stream()
                        .anyMatch(c -> "http://snomed.info/sct".equals(c.getSystem())
                                && "288328004".equals(c.getCode())));
        Assert.assertEquals("code text must be 'Bee venom'", "Bee venom", allergy.getCode().getText());

        // onsetDateTime: 2009-11-15
        Assert.assertNotNull("onsetDateTime must be present", allergy.getOnset());

        // lastOccurrence: 2009-11-15
        Assert.assertNotNull("lastOccurrence must be present", allergy.getLastOccurrence());

        // _clinicalStatus extension: active
        assertExtensionHasSnomedOrActStatusCode(
                allergy.getClinicalStatusElement().getExtension(), "active", "clinical status");

        // _category extension: Allergy to substance (419199007)
        Assert.assertFalse("_category extensions must be present",
                allergy.getCategory().get(0).getExtension().isEmpty());
        assertExtensionHasSnomedCode(
                allergy.getCategory().get(0).getExtension(), "419199007", "category");

        // _criticality extension: Severe (24484000)
        assertExtensionHasSnomedCode(
                allergy.getCriticalityElement().getExtension(), "24484000", "criticality");

        // reaction[0].manifestation: Nausea and vomiting (16932000)
        Assert.assertFalse("reaction must be present", allergy.getReaction().isEmpty());
        final AllergyIntolerance.AllergyIntoleranceReactionComponent reaction = allergy.getReaction().get(0);
        Assert.assertTrue("reaction manifestation must contain SNOMED 16932000",
                reaction.getManifestation().stream()
                        .flatMap(m -> m.getCoding().stream())
                        .anyMatch(c -> "http://snomed.info/sct".equals(c.getSystem())
                                && "16932000".equals(c.getCode())));

        // reaction[0]._severity extension: Severe (24484000)
        assertExtensionHasSnomedCode(
                reaction.getSeverityElement().getExtension(), "24484000", "reaction severity");
    }

    private void assertFlatJson(final JsonObject flat) {
        Assert.assertNotNull("flatJson must not be null", flat);

        // causative agent: Bee venom (SNOMED 288328004)
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/causativeagent|code", "288328004");
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/causativeagent|terminology", "http://snomed.info/sct");
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/causativeagent|value", "Bee venom");

        // allergy category: Allergy to substance (419199007)
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/allergycategory|code", "419199007");
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/allergycategory|terminology", "http://snomed.info/sct");
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/allergycategory|value", "Allergy to substance");

        // allergy status: active
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/allergystatus|code", "active");
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/allergystatus|terminology", "http://hl7.org/fhir/v3/ActStatus");
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/allergystatus|value", "Active");

        // start date
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/startdatetime", "2009-11-15T00:00:00");

        // criticality: Severe (24484000)
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/criticality|code", "24484000");
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/criticality|terminology", "http://snomed.info/sct");
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/criticality|value", "Severe");

        // reaction:0 symptom:0: Nausea and vomiting (16932000)
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/reaction:0/symptom:0|code", "16932000");
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/reaction:0/symptom:0|terminology", "http://snomed.info/sct");
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/reaction:0/symptom:0|value", "Nausea and vomiting");

        // reaction:0 severity: Severe (24484000)
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/reaction:0/severity|code", "24484000");
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/reaction:0/severity|terminology", "http://snomed.info/sct");
        assertFlat(flat, "datahub-allergyintolerance-2017/allergyintolerance/reaction:0/severity|value", "Severe");
    }

    private void assertFlat(final JsonObject flat, final String key, final String expectedValue) {
        Assert.assertTrue("flat JSON must contain key: " + key, flat.has(key));
        Assert.assertEquals("flat JSON key '" + key + "' must equal '" + expectedValue + "'",
                expectedValue, flat.get(key).getAsString());
    }

    private void assertExtensionHasSnomedCode(
            final List<Extension> extensions, final String expectedCode, final String label) {
        Assert.assertFalse(label + " extensions must be present", extensions.isEmpty());
        final boolean found = extensions.stream()
                .filter(ext -> ext.getValue() instanceof CodeableConcept)
                .map(ext -> (CodeableConcept) ext.getValue())
                .flatMap(cc -> cc.getCoding().stream())
                .anyMatch(c -> "http://snomed.info/sct".equals(c.getSystem())
                        && expectedCode.equals(c.getCode()));
        Assert.assertTrue(label + " extension must contain SNOMED code " + expectedCode, found);
    }

    private void assertExtensionHasSnomedOrActStatusCode(
            final List<Extension> extensions, final String expectedCode, final String label) {
        Assert.assertFalse(label + " extensions must be present", extensions.isEmpty());
        final boolean found = extensions.stream()
                .filter(ext -> ext.getValue() instanceof CodeableConcept)
                .map(ext -> (CodeableConcept) ext.getValue())
                .flatMap(cc -> cc.getCoding().stream())
                .anyMatch(c -> expectedCode.equals(c.getCode()));
        Assert.assertTrue(label + " extension must contain code " + expectedCode, found);
    }
}
