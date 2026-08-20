package com.syntaric.openfhir.util;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class OpenEhrPopulatorTest {

    private OpenEhrPopulator populator;
    private JsonObject flat;

    @Before
    public void setUp() {
        populator = new OpenEhrPopulator(new OpenFhirMapperUtils(), null, new NoOpPrePostOpenEhrPopulator(), new OpenFhirStringUtils());
        flat = new JsonObject();
    }

    @Test
    public void contextStartTimeRetainsEarliestValue() {
        JsonObject localFlat = new JsonObject();
        String path = "test_template/context/start_time";

        populator.addToConstructingFlat(path, "2024-05-05T10:00:00", localFlat);
        populator.addToConstructingFlat(path, "2024-05-05T12:00:00", localFlat);
        populator.addToConstructingFlat(path, "2024-05-05T09:00:00", localFlat);

        Assert.assertEquals("2024-05-05T09:00:00", localFlat.get(path).getAsString());
    }

    @Test
    public void contextEndTimeRetainsLatestValue() {
        JsonObject localFlat = new JsonObject();
        String path = "test_template/context/_end_time";

        populator.addToConstructingFlat(path, "2024-05-05T10:00:00", localFlat);
        populator.addToConstructingFlat(path, "2024-05-05T09:00:00", localFlat);
        populator.addToConstructingFlat(path, "2024-05-05T11:00:00", localFlat);

        Assert.assertEquals("2024-05-05T11:00:00", localFlat.get(path).getAsString());
    }

    @Test
    public void setNullFlavourForDataAbsentReasonCoding() {
        Coding coding = new Coding(
                "http://terminology.hl7.org/CodeSystem/data-absent-reason",
                "unknown",
                "Unknown");

        boolean handled = populator.setNullFlavourForDataAbsentReason("test/value/null_flavour", coding, flat);

        Assert.assertTrue(handled);
        Assert.assertEquals("unknown", flat.get("test/value/null_flavour|value").getAsString());
        Assert.assertEquals("253", flat.get("test/value/null_flavour|code").getAsString());
        Assert.assertEquals("openehr", flat.get("test/value/null_flavour|terminology").getAsString());
    }

    @Test
    public void setOpenEhr() {
        Coding  coding = new Coding(
                "http://terminology.hl7.org/CodeSystem/data-absent-reason",
                "asked-declined",
                "Asked Declined");

        populator.setOpenEhrValue(null, "test/value/null_flavour", coding, FhirConnectConst.CODE_PHRASE, false, flat, null, null);

        Assert.assertEquals("masked", flat.get("test/value/null_flavour|value").getAsString());
        Assert.assertEquals("272", flat.get("test/value/null_flavour|code").getAsString());
        Assert.assertEquals("openehr", flat.get("test/value/null_flavour|terminology").getAsString());
    }

    /**
     * A DV_IDENTIFIER whose |type and |assigner carried no system of their own gets the
     * {@code http://openehr.org/identifier/...} placeholder on the way out (see IdentifierParser).
     * That placeholder means "no real system" and must not surface in the openEHR value on the way
     * back, the way it already does not for |issuer.
     */
    @Test
    public void identifierPlaceholderSystemIsStrippedOnTheWayBack() {
        final Identifier identifier = new Identifier();
        identifier.setValue("RX-SYNTH-000771");
        identifier.setSystem("http://openehr.org/identifier/Synthetic Clinic Branch");
        identifier.getType().addCoding(
                new Coding("http://openehr.org/identifier/type", "Prescription number", null));

        final Reference assigner = new Reference();
        assigner.setDisplay("Synthetic Clinic Branch");
        assigner.setIdentifier(new Identifier()
                                       .setSystem("http://openehr.org/identifier/assigner")
                                       .setValue("Synthetic Clinic Branch"));
        identifier.setAssigner(assigner);

        populator.setOpenEhrValue(null, "test/order_identifier", identifier,
                                  FhirConnectConst.DV_IDENTIFIER, false, flat, null, null);

        Assert.assertEquals("RX-SYNTH-000771", flat.get("test/order_identifier|id").getAsString());
        Assert.assertEquals("Synthetic Clinic Branch", flat.get("test/order_identifier|issuer").getAsString());
        Assert.assertEquals("Prescription number", flat.get("test/order_identifier|type").getAsString());
        Assert.assertEquals("Synthetic Clinic Branch", flat.get("test/order_identifier|assigner").getAsString());
    }

    /**
     * A system that genuinely came from the source FHIR is not a placeholder and stays, encoded as
     * {@code system::value} — stripping it would lose real data.
     */
    @Test
    public void identifierRealSystemIsPreserved() {
        final Identifier identifier = new Identifier();
        identifier.setValue("12345");
        identifier.getType().addCoding(
                new Coding("http://terminology.hl7.org/CodeSystem/v2-0203", "MR", null));

        final Reference assigner = new Reference();
        assigner.setDisplay("Some Hospital");
        assigner.setIdentifier(new Identifier()
                                       .setSystem("http://example.org/orgs")
                                       .setValue("HOSP-1"));
        identifier.setAssigner(assigner);

        populator.setOpenEhrValue(null, "test/order_identifier", identifier,
                                  FhirConnectConst.DV_IDENTIFIER, false, flat, null, null);

        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/v2-0203::MR",
                            flat.get("test/order_identifier|type").getAsString());
        Assert.assertEquals("http://example.org/orgs::HOSP-1",
                            flat.get("test/order_identifier|assigner").getAsString());
    }
}
