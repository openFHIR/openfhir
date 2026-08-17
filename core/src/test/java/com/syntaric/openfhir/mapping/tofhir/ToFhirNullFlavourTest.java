package com.syntaric.openfhir.mapping.tofhir;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.util.FhirInstanceCreatorUtility;
import com.syntaric.openfhir.util.OpenEhrPopulator;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Verifies that an openEHR {@code null_flavour} is reconstructed as a FHIR
 * {@code data-absent-reason} extension on the FHIR primitive it belongs to.
 */
public class ToFhirNullFlavourTest {

    private static final String R4_MODEL_PACKAGE = "org.hl7.fhir.r4.model.";
    private static final String CITY_ELEMENT = "person/personendaten/person/address:0/city:0";

    private ToFhirNullFlavour toFhirNullFlavour;

    @Before
    public void setUp() {
        final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
        toFhirNullFlavour = new ToFhirNullFlavour(openFhirStringUtils,
                                                  new FhirInstanceCreatorUtility(openFhirStringUtils));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------------------------------

    private JsonObject flatWithNullFlavour(final String elementPath, final String code, final String value) {
        final JsonObject flat = new JsonObject();
        if (code != null) {
            flat.addProperty(elementPath + "/_null_flavour|code", code);
        }
        if (value != null) {
            flat.addProperty(elementPath + "/_null_flavour|value", value);
        }
        flat.addProperty(elementPath + "/_null_flavour|terminology", "openehr");
        return flat;
    }

    private MappingHelper helper() {
        final MappingHelper helper = new MappingHelper();
        helper.setMappingName("city");
        helper.setFhir("city");
        helper.setFullOpenEhrFlatPath("person/personendaten/person/address[n]/city[n]");
        return helper;
    }

    /**
     * A data point as produced by the extractor for an ELEMENT that only carries a null flavour:
     * the value leaf is appended but nothing was actually extracted.
     */
    private List<DataWithIndex> extractedFor(final String elementPath) {
        return List.of(new DataWithIndex(new CodeableConcept(), 0, elementPath + "/coded_text_value",
                                          "DV_CODED_TEXT"));
    }

    private String dataAbsentReasonOf(final org.hl7.fhir.r4.model.Element element) {
        final Extension extension = element.getExtensionByUrl(OpenEhrPopulator.DATA_ABSENT_REASON_URL);
        return extension == null ? null : ((CodeType) extension.getValue()).getValue();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Code translation
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void maskedNullFlavourBecomesMaskedDataAbsentReason() {
        final StringType city = new StringType();

        final boolean handled = toFhirNullFlavour.handleNullFlavour(helper(), city, extractedFor(CITY_ELEMENT),
                                                                    flatWithNullFlavour(CITY_ELEMENT, "272", "masked"),
                                                                    R4_MODEL_PACKAGE);

        Assert.assertTrue(handled);
        Assert.assertEquals("masked", dataAbsentReasonOf(city));
    }

    @Test
    public void unknownNullFlavourBecomesUnknownDataAbsentReason() {
        final StringType city = new StringType();

        toFhirNullFlavour.handleNullFlavour(helper(), city, extractedFor(CITY_ELEMENT),
                                            flatWithNullFlavour(CITY_ELEMENT, "253", "unknown"), R4_MODEL_PACKAGE);

        Assert.assertEquals("unknown", dataAbsentReasonOf(city));
    }

    @Test
    public void noInformationNullFlavourBecomesUnknownDataAbsentReason() {
        final StringType city = new StringType();

        toFhirNullFlavour.handleNullFlavour(helper(), city, extractedFor(CITY_ELEMENT),
                                            flatWithNullFlavour(CITY_ELEMENT, "271", "no information"),
                                            R4_MODEL_PACKAGE);

        Assert.assertEquals("unknown", dataAbsentReasonOf(city));
    }

    @Test
    public void notApplicableNullFlavourBecomesNotApplicableDataAbsentReason() {
        final StringType city = new StringType();

        toFhirNullFlavour.handleNullFlavour(helper(), city, extractedFor(CITY_ELEMENT),
                                            flatWithNullFlavour(CITY_ELEMENT, "273", "not applicable"),
                                            R4_MODEL_PACKAGE);

        Assert.assertEquals("not-applicable", dataAbsentReasonOf(city));
    }

    @Test
    public void nullFlavourIsResolvedFromValueWhenCodeIsAbsent() {
        final StringType city = new StringType();

        toFhirNullFlavour.handleNullFlavour(helper(), city, extractedFor(CITY_ELEMENT),
                                            flatWithNullFlavour(CITY_ELEMENT, null, "masked"), R4_MODEL_PACKAGE);

        Assert.assertEquals("masked", dataAbsentReasonOf(city));
    }

    @Test
    public void untranslatableNullFlavourIsIgnored() {
        final StringType city = new StringType();

        final boolean handled = toFhirNullFlavour.handleNullFlavour(helper(), city, extractedFor(CITY_ELEMENT),
                                                                    flatWithNullFlavour(CITY_ELEMENT, "999",
                                                                                        "something else"),
                                                                    R4_MODEL_PACKAGE);

        Assert.assertFalse(handled);
        Assert.assertTrue(city.getExtension().isEmpty());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Target resolution
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void repeatingPrimitiveGetsExtensionOnTheOccurrenceJustInstantiated() {
        final String lineElement = "person/personendaten/person/address:0/line:1";
        final StringType alreadyMappedLine = new StringType("Musterstraße 1");
        final StringType absentLine = new StringType();
        final MappingHelper helper = helper();
        helper.setMappingName("line");
        helper.setFhir("line");

        final boolean handled = toFhirNullFlavour.handleNullFlavour(helper,
                                                                    List.of(alreadyMappedLine, absentLine),
                                                                    extractedFor(lineElement),
                                                                    flatWithNullFlavour(lineElement, "272", "masked"),
                                                                    R4_MODEL_PACKAGE);

        Assert.assertTrue(handled);
        Assert.assertEquals("masked", dataAbsentReasonOf(absentLine));
        Assert.assertTrue(alreadyMappedLine.getExtension().isEmpty());
    }

    @Test
    public void primitiveThatAlreadyHasAValueIsLeftUntouched() {
        final StringType city = new StringType("Berlin");

        final boolean handled = toFhirNullFlavour.handleNullFlavour(helper(), city, extractedFor(CITY_ELEMENT),
                                                                    flatWithNullFlavour(CITY_ELEMENT, "272", "masked"),
                                                                    R4_MODEL_PACKAGE);

        Assert.assertFalse(handled);
        Assert.assertTrue(city.getExtension().isEmpty());
    }

    /**
     * Complex datatypes such as {@code Observation.value[x]} have a dedicated sibling
     * {@code dataAbsentReason} element and must not receive the extension.
     */
    @Test
    public void complexDatatypeIsNotGivenTheExtension() {
        final Quantity quantity = new Quantity();

        final boolean handled = toFhirNullFlavour.handleNullFlavour(helper(), quantity, extractedFor(CITY_ELEMENT),
                                                                    flatWithNullFlavour(CITY_ELEMENT, "272", "masked"),
                                                                    R4_MODEL_PACKAGE);

        Assert.assertFalse(handled);
        Assert.assertTrue(quantity.getExtension().isEmpty());
    }

    @Test
    public void elementWithoutNullFlavourIsLeftUntouched() {
        final StringType city = new StringType();
        final JsonObject flat = new JsonObject();
        flat.addProperty(CITY_ELEMENT, "Berlin");

        final boolean handled = toFhirNullFlavour.handleNullFlavour(helper(), city, extractedFor(CITY_ELEMENT), flat,
                                                                    R4_MODEL_PACKAGE);

        Assert.assertFalse(handled);
        Assert.assertTrue(city.getExtension().isEmpty());
    }

    @Test
    public void nullFlavourOfAnotherOccurrenceIsNotApplied() {
        final StringType city = new StringType();

        final boolean handled = toFhirNullFlavour.handleNullFlavour(
                helper(), city, extractedFor(CITY_ELEMENT),
                flatWithNullFlavour("person/personendaten/person/address:1/city:0", "272", "masked"),
                R4_MODEL_PACKAGE);

        Assert.assertFalse(handled);
        Assert.assertTrue(city.getExtension().isEmpty());
    }

    /**
     * Without an extracted data point the occurrence indices are unknown, so the still
     * {@code [n]}-bearing mapping path must not be used to look up a null flavour.
     */
    @Test
    public void unresolvedOccurrencePathIsNotUsedForLookup() {
        final StringType city = new StringType();

        final boolean handled = toFhirNullFlavour.handleNullFlavour(helper(), city, List.of(),
                                                                    flatWithNullFlavour(CITY_ELEMENT, "272", "masked"),
                                                                    R4_MODEL_PACKAGE);

        Assert.assertFalse(handled);
        Assert.assertTrue(city.getExtension().isEmpty());
    }

    @Test
    public void extensionIsNotAddedTwice() {
        final StringType city = new StringType();
        final JsonObject flat = flatWithNullFlavour(CITY_ELEMENT, "272", "masked");

        toFhirNullFlavour.handleNullFlavour(helper(), city, extractedFor(CITY_ELEMENT), flat, R4_MODEL_PACKAGE);
        final boolean handledAgain = toFhirNullFlavour.handleNullFlavour(helper(), city, extractedFor(CITY_ELEMENT),
                                                                          flat, R4_MODEL_PACKAGE);

        Assert.assertFalse(handledAgain);
        Assert.assertEquals(1, city.getExtension().size());
    }

    @Test
    public void nullArgumentsAreHandledGracefully() {
        Assert.assertFalse(toFhirNullFlavour.handleNullFlavour(null, new StringType(), List.of(), new JsonObject(),
                                                                R4_MODEL_PACKAGE));
        Assert.assertFalse(toFhirNullFlavour.handleNullFlavour(helper(), null, List.of(), new JsonObject(),
                                                                R4_MODEL_PACKAGE));
        Assert.assertFalse(toFhirNullFlavour.handleNullFlavour(helper(), new StringType(), List.of(), null,
                                                                R4_MODEL_PACKAGE));
    }
}
