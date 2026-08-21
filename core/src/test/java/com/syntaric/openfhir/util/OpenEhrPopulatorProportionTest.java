package com.syntaric.openfhir.util;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static com.syntaric.openfhir.mapping.helpers.parser.QuantityParser.PROPORTION_DENOMINATOR_EXTENSION;
import static com.syntaric.openfhir.mapping.helpers.parser.QuantityParser.PROPORTION_KIND_EXTENSION;

/**
 * The return leg of the DV_PROPORTION mapping. FHIR has no field naming the kind of proportion, so
 * {@code |type} has to be derived from the parts that do arrive — which is every case on this leg,
 * and always the case when the source is a device.
 * <p>
 * {@code |type} was previously hardcoded to {@code 2} (percent), which was asserted even when no
 * denominator had been written.
 */
public class OpenEhrPopulatorProportionTest {

    private static final String PATH = "test_template/proportion";

    private OpenEhrPopulator populator;

    @Before
    public void setUp() {
        populator = new OpenEhrPopulator(new OpenFhirMapperUtils(), null, new NoOpPrePostOpenEhrPopulator(),
                new OpenFhirStringUtils());
    }

    /** A pulse oximeter emits a bare percent Quantity: no extensions, and none to be expected. */
    @Test
    public void devicePercentBecomesAPercentProportion() {
        final JsonObject flat = proportionFor(percent(97));

        Assert.assertEquals(97.0, flat.get(PATH + "|numerator").getAsDouble(), 0.0);
        Assert.assertEquals(100.0, flat.get(PATH + "|denominator").getAsDouble(), 0.0);
        Assert.assertEquals("2", flat.get(PATH + "|type").getAsString());
    }

    /** A device may name the percent in the unit rather than the code. */
    @Test
    public void percentIsRecognisedFromTheUnitToo() {
        final Quantity quantity = new Quantity();
        quantity.setValue(97);
        quantity.setUnit("%");

        final JsonObject flat = proportionFor(quantity);

        Assert.assertEquals(100.0, flat.get(PATH + "|denominator").getAsDouble(), 0.0);
        Assert.assertEquals("2", flat.get(PATH + "|type").getAsString());
    }

    /**
     * A Quantity that describes no proportion gets no denominator, and so must get no {@code |type}
     * either — a bare {@code |type} of 2 claims a denominator of 100 that is not there, which is an
     * invalid DV_PROPORTION.
     */
    @Test
    public void nonProportionQuantityGetsNoTypeAtAll() {
        final Quantity quantity = new Quantity();
        quantity.setValue(70);
        quantity.setCode("mm[Hg]");

        final JsonObject flat = proportionFor(quantity);

        Assert.assertFalse("a |type without a |denominator is an invalid DV_PROPORTION",
                           flat.has(PATH + "|type"));
        Assert.assertFalse(flat.has(PATH + "|denominator"));
        Assert.assertEquals(70.0, flat.get(PATH + "|numerator").getAsDouble(), 0.0);
    }

    /** Denominators openFHIR itself carried across get their kind derived from the denominator. */
    @Test
    public void carriedDenominatorDecidesTheKind() {
        Assert.assertEquals("1", kindFor(ratio(0.5, "1")));
        Assert.assertEquals("4", kindFor(ratio(3, "4")));
        Assert.assertEquals("0", kindFor(ratio(1.5, "4")));
    }

    /**
     * A declared kind is preserved rather than re-derived: a denominator cannot distinguish a
     * fraction (3) from an integer fraction (4), so the round trip depends on carrying it.
     */
    @Test
    public void declaredKindWinsOverDerivation() {
        final Quantity quantity = percent(50);
        quantity.addExtension(PROPORTION_KIND_EXTENSION, new StringType("3"));

        final JsonObject flat = proportionFor(quantity);

        Assert.assertEquals("3", flat.get(PATH + "|type").getAsString());
        Assert.assertEquals(50.0, flat.get(PATH + "|numerator").getAsDouble(), 0.0);
        Assert.assertEquals(100.0, flat.get(PATH + "|denominator").getAsDouble(), 0.0);
    }

    /** The leg must behave the same whichever FHIR version the resource arrived as. */
    @Test
    public void everyFhirVersionDerivesTheSameKind() {
        final org.hl7.fhir.dstu3.model.Quantity stu3 = new org.hl7.fhir.dstu3.model.Quantity();
        stu3.setValue(97).setCode("%");
        final org.hl7.fhir.r4b.model.Quantity r4b = new org.hl7.fhir.r4b.model.Quantity();
        r4b.setValue(97).setCode("%");
        final org.hl7.fhir.r5.model.Quantity r5 = new org.hl7.fhir.r5.model.Quantity();
        r5.setValue(97).setCode("%");

        for (final org.hl7.fhir.instance.model.api.IBase quantity : new org.hl7.fhir.instance.model.api.IBase[]{stu3, r4b, r5}) {
            final JsonObject flat = new JsonObject();
            populator.setOpenEhrValue(null, PATH, quantity, FhirConnectConst.DV_PROPORTION, false, flat, null, null);
            Assert.assertEquals("2", flat.get(PATH + "|type").getAsString());
            Assert.assertEquals(100.0, flat.get(PATH + "|denominator").getAsDouble(), 0.0);
        }
    }

    private String kindFor(final Quantity quantity) {
        return proportionFor(quantity).get(PATH + "|type").getAsString();
    }

    private JsonObject proportionFor(final org.hl7.fhir.instance.model.api.IBase quantity) {
        final JsonObject flat = new JsonObject();
        populator.setOpenEhrValue(null, PATH, quantity, FhirConnectConst.DV_PROPORTION, false, flat, null, null);
        return flat;
    }

    private static Quantity percent(final double numerator) {
        final Quantity quantity = new Quantity();
        quantity.setValue(numerator);
        quantity.setCode("%");
        quantity.setSystem("http://unitsofmeasure.org");
        return quantity;
    }

    private static Quantity ratio(final double numerator, final String denominator) {
        final Quantity quantity = new Quantity();
        quantity.setValue(numerator);
        quantity.addExtension(PROPORTION_DENOMINATOR_EXTENSION, new DecimalType(denominator));
        return quantity;
    }
}
