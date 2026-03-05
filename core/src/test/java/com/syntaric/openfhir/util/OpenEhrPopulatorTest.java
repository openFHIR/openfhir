package com.syntaric.openfhir.util;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import org.hl7.fhir.r4.model.Coding;
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

        populator.setOpenEhrValue(null, "test/value/null_flavour", coding, FhirConnectConst.CODE_PHRASE, flat, null, null);

        Assert.assertEquals("masked", flat.get("test/value/null_flavour|value").getAsString());
        Assert.assertEquals("272", flat.get("test/value/null_flavour|code").getAsString());
        Assert.assertEquals("openehr", flat.get("test/value/null_flavour|terminology").getAsString());
    }
}
