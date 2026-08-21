package com.syntaric.openfhir.util;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import org.hl7.fhir.r4.model.Duration;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * The return leg of the DV_DURATION mapping. A FHIR Duration is a Quantity, so its unit lives in the
 * UCUM code; writing only the magnitude would turn {@code 30 min} into {@code "30"}, which openEHR
 * cannot read back as a duration.
 */
public class OpenEhrPopulatorDurationTest {

    private static final String PATH = "test_template/duration";

    private OpenEhrPopulator populator;
    private JsonObject flat;

    @Before
    public void setUp() {
        populator = new OpenEhrPopulator(new OpenFhirMapperUtils(), null, new NoOpPrePostOpenEhrPopulator(),
                new OpenFhirStringUtils());
        flat = new JsonObject();
    }

    /** Zero is a duration, not an absent value, and must survive as PT0S rather than "0". */
    @Test
    public void zeroDurationRoundTripsAsPt0s() {
        setDuration(duration(0, "s"));

        Assert.assertEquals("PT0S", flat.get(PATH).getAsString());
    }

    @Test
    public void timeUnitsKeepTheirComponent() {
        Assert.assertEquals("PT45S", isoFor(duration(45, "s")));
        Assert.assertEquals("PT30M", isoFor(duration(30, "min")));
        Assert.assertEquals("PT8H", isoFor(duration(8, "h")));
    }

    @Test
    public void dateUnitsKeepTheirComponent() {
        Assert.assertEquals("P10D", isoFor(duration(10, "d")));
        Assert.assertEquals("P3W", isoFor(duration(3, "wk")));
        Assert.assertEquals("P6M", isoFor(duration(6, "mo")));
        Assert.assertEquals("P1Y", isoFor(duration(1, "a")));
    }

    /**
     * A fractional hour or minute has no integer ISO component, so it is carried on the seconds
     * field rather than being rounded away.
     */
    @Test
    public void fractionalTimeUnitsFallBackToSeconds() {
        Assert.assertEquals("PT1800S", isoFor(duration(0.5, "h")));
        Assert.assertEquals("PT90S", isoFor(duration(1.5, "min")));
        Assert.assertEquals("PT0.5S", isoFor(duration(0.5, "s")));
    }

    /** The unit is used when no code was supplied, since a Duration is still readable that way. */
    @Test
    public void unitIsUsedWhenCodeIsAbsent() {
        final Duration duration = new Duration();
        duration.setValue(15);
        duration.setUnit("min");

        setDuration(duration);

        Assert.assertEquals("PT15M", flat.get(PATH).getAsString());
    }

    /**
     * A Quantity whose code is not a UCUM time unit is not a duration; the previous behaviour of
     * writing the bare magnitude is kept so unrelated mappings are unaffected.
     */
    @Test
    public void nonTimeUnitFallsBackToMagnitude() {
        setDuration(duration(500, "mg"));

        Assert.assertEquals("500.0", flat.get(PATH).getAsString());
    }

    /**
     * A Duration pointed at a text node keeps its unit, otherwise the text reads as a bare number.
     */
    @Test
    public void durationToDvTextKeepsIsoForm() {
        populator.setOpenEhrValue(null, PATH, duration(30, "min"), FhirConnectConst.DV_TEXT, false, flat, null, null);

        Assert.assertEquals("PT30M", flat.get(PATH).getAsString());
    }

    /** A plain Quantity on a text node must not be reinterpreted as a duration. */
    @Test
    public void plainQuantityToDvTextIsUnchanged() {
        final Quantity quantity = new Quantity();
        quantity.setValue(30);
        quantity.setCode("mg");

        populator.setOpenEhrValue(null, PATH, quantity, FhirConnectConst.DV_TEXT, false, flat, null, null);

        Assert.assertEquals("30", flat.get(PATH).getAsString());
    }

    private String isoFor(final Duration duration) {
        final JsonObject localFlat = new JsonObject();
        populator.setOpenEhrValue(null, PATH, duration, FhirConnectConst.DV_DURATION, false, localFlat, null, null);
        return localFlat.get(PATH).getAsString();
    }

    private void setDuration(final Quantity duration) {
        populator.setOpenEhrValue(null, PATH, duration, FhirConnectConst.DV_DURATION, false, flat, null, null);
    }

    /**
     * The return leg must work for whichever FHIR version the resource came from, not just R4.
     */
    @Test
    public void everyFhirVersionRendersBackToIso() {
        final org.hl7.fhir.dstu3.model.Duration stu3 = new org.hl7.fhir.dstu3.model.Duration();
        stu3.setValue(30);
        stu3.setCode("min");
        final org.hl7.fhir.r4b.model.Duration r4b = new org.hl7.fhir.r4b.model.Duration();
        r4b.setValue(0);
        r4b.setCode("s");
        final org.hl7.fhir.r5.model.Duration r5 = new org.hl7.fhir.r5.model.Duration();
        r5.setValue(8);
        r5.setCode("h");

        final JsonObject stu3Flat = new JsonObject();
        final JsonObject r4bFlat = new JsonObject();
        final JsonObject r5Flat = new JsonObject();
        populator.setOpenEhrValue(null, PATH, stu3, FhirConnectConst.DV_DURATION, false, stu3Flat, null, null);
        populator.setOpenEhrValue(null, PATH, r4b, FhirConnectConst.DV_DURATION, false, r4bFlat, null, null);
        populator.setOpenEhrValue(null, PATH, r5, FhirConnectConst.DV_DURATION, false, r5Flat, null, null);

        Assert.assertEquals("PT30M", stu3Flat.get(PATH).getAsString());
        Assert.assertEquals("PT0S", r4bFlat.get(PATH).getAsString());
        Assert.assertEquals("PT8H", r5Flat.get(PATH).getAsString());
    }

    private static Duration duration(final double value, final String code) {
        final Duration duration = new Duration();
        duration.setValue(value);
        duration.setCode(code);
        duration.setSystem("http://unitsofmeasure.org");
        return duration;
    }
}
