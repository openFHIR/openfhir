package com.syntaric.openfhir.mapping.helpers;

import com.syntaric.openfhir.mapping.helpers.AqlToFlatPathConverter.Result;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import java.io.InputStream;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openehr.schemas.v1.TemplateDocument;

public class AqlToFlatPathConverterTest {

    private AqlToFlatPathConverter converter;

    @Before
    public void setUp() {
        converter = new AqlToFlatPathConverter(new OpenFhirStringUtils());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Growth Chart
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void growthChart_weight() {
        final WebTemplate wt = loadWebTemplate("/growth_chart/Growth chart.opt");

        // weight: multi-occurrence event → any_event[n]/weight
        assertConversion(wt,
                         "openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004]",
                         "growth_chart/body_weight/any_event[n]/weight",
                         "DV_QUANTITY",
                         true);
    }

    @Test
    public void fall_entlassung() {
        final WebTemplate wt = loadWebTemplate("/kds/fall/KDS_Fall_einfach.opt");

        // weight: multi-occurrence event → any_event[n]/weight
        assertConversion(wt,
                         "openEHR-EHR-ADMIN_ENTRY.episode_institution_local.v0/data[at0001]/items[at0006]",
                         "kds_fall_einfach/institutionsaufenthalt/entlassungsgrundersteundzweitestelle[n]",
                         "DV_CODED_TEXT",
                         true);
    }

    @Test
    public void growthChart_weightTime() {
        final WebTemplate wt = loadWebTemplate("/growth_chart/Growth chart.opt");

        // time sits directly under the event node
        assertConversion(wt,
                         "openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/time",
                         "growth_chart/body_weight/any_event[n]/time",
                         "DV_DATE_TIME",
                         true);
    }

    @Test
    public void growthChart_comment() {
        final WebTemplate wt = loadWebTemplate("/growth_chart/Growth chart.opt");

        assertConversion(wt,
                         "openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0024]",
                         "growth_chart/body_weight/any_event[n]/comment",
                         "DV_TEXT",
                         true);
    }

    @Test
    public void growthChart_stateOfDress() {
        final WebTemplate wt = loadWebTemplate("/growth_chart/Growth chart.opt");

        assertConversion(wt,
                         "openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/state[at0008]/items[at0009]",
                         "growth_chart/body_weight/any_event[n]/state_of_dress",
                         "DV_CODED_TEXT",
                         true);
    }

    @Test
    public void growthChart_height() {
        final WebTemplate wt = loadWebTemplate("/growth_chart/Growth chart.opt");

        assertConversion(wt,
                         "openEHR-EHR-OBSERVATION.height.v2/data[at0001]/events[at0002]/data[at0003]/items[at0004]",
                         "growth_chart/height_length/any_event[n]/height_length",
                         "DV_QUANTITY",
                         true);
    }

    @Test
    public void growthChart_bmi() {
        final WebTemplate wt = loadWebTemplate("/growth_chart/Growth chart.opt");

        assertConversion(wt,
                         "openEHR-EHR-OBSERVATION.body_mass_index.v2/data[at0001]/events[at0002]/data[at0003]/items[at0004]",
                         "growth_chart/body_mass_index/any_event[n]/body_mass_index",
                         "DV_QUANTITY",
                         true);
    }

    @Test
    public void growthChart_bmiTime() {
        final WebTemplate wt = loadWebTemplate("/growth_chart/Growth chart.opt");

        assertConversion(wt,
                         "openEHR-EHR-OBSERVATION.body_mass_index.v2/data[at0001]/events[at0002]/time",
                         "growth_chart/body_mass_index/any_event[n]/time",
                         "DV_DATE_TIME",
                         true);
    }

    @Test
    public void growthChart_headCircumference() {
        final WebTemplate wt = loadWebTemplate("/growth_chart/Growth chart.opt");

        assertConversion(wt,
                         "openEHR-EHR-OBSERVATION.head_circumference.v1/data[at0001]/events[at0010]/data[at0003]/items[at0004]",
                         "growth_chart/head_circumference/any_event[n]/head_circumference",
                         "DV_QUANTITY",
                         true);
    }

    @Test
    public void growthChart_headCircumferenceTime() {
        final WebTemplate wt = loadWebTemplate("/growth_chart/Growth chart.opt");

        assertConversion(wt,
                         "openEHR-EHR-OBSERVATION.head_circumference.v1/data[at0001]/events[at0010]/time",
                         "growth_chart/head_circumference/any_event[n]/time",
                         "DV_DATE_TIME",
                         true);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Medication Order
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void medicationOrder_note() {
        final WebTemplate wt = loadWebTemplate("/medication_order/medication order.opt");

        // items[at0044] = additional_instruction (multi) in the OPT
        assertConversion(wt,
                         "openEHR-EHR-INSTRUCTION.medication_order.v2/activities[at0001]/description[at0002]/items[at0044]",
                         "medication_order/medication_order/order[n]/additional_instruction[n]",
                         "DV_TEXT",
                         true);
    }

    @Test
    public void medicationOrder_time() {
        final WebTemplate wt = loadWebTemplate("/medication_order/medication order.opt");

        assertConversion(wt,
                         "openEHR-EHR-INSTRUCTION.medication_order.v2/activities[at0001]/description[at0002]/items[at0113]/items[at0012]",
                         "medication_order/medication_order/order[n]/order_details/order_start_date_time",
                         "DV_DATE_TIME",
                         true);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Segment absent under the matched parent but present elsewhere in the template
    // -----------------------------------------------------------------------------------------------------------------

    private static final String IPS_PROBLEM_QUALIFIER =
            "$composition/content[openEHR-EHR-SECTION.adhoc.v1 and name/value='Problem List']"
                    + "/items[openEHR-EHR-EVALUATION.problem_diagnosis.v1]/data[at0001]"
                    + "/items[openEHR-EHR-CLUSTER.problem_qualifier.v2]";

    @Test
    public void ips_segmentNotUnderParent_isUnresolved() {
        final WebTemplate wt = loadWebTemplate("/ips/International Patient Summary.opt");

        // at0004 is not in the template under the problem qualifier, but an items[at0004] exists elsewhere
        // (specific_time of timing_daily under the medication summary)
        final Result result = converter.convert(IPS_PROBLEM_QUALIFIER + "/items[at0004]", wt);

        Assert.assertFalse(result.valid());
        Assert.assertEquals("items[at0004]", result.unresolvedSegment());
        Assert.assertNull(result.rmType());
        Assert.assertFalse(result.flatPath(), result.flatPath().contains("specific_time"));
        Assert.assertTrue(result.flatPath(), result.flatPath().endsWith("/problem_diagnosis_qualifier"));
    }

    @Test
    public void ips_segmentUnderParent_isResolved() {
        final WebTemplate wt = loadWebTemplate("/ips/International Patient Summary.opt");

        final Result result = converter.convert(IPS_PROBLEM_QUALIFIER + "/items[at0003]", wt);

        Assert.assertTrue(result.valid());
        Assert.assertNull(result.unresolvedSegment());
        Assert.assertTrue(result.flatPath(), result.flatPath().contains("/problem_diagnosis_qualifier/"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------------------------------

    private void assertConversion(final WebTemplate webTemplate,
                                  final String aqlPath,
                                  final String expectedFlatPath,
                                  final String expectedRmType,
                                  final boolean expectedValid) {
        final Result result = converter.convert(aqlPath, webTemplate);
        Assert.assertEquals("flatPath for " + aqlPath, expectedFlatPath, result.flatPath());
        Assert.assertTrue("rmType for " + aqlPath, result.possibleTypes().contains(expectedRmType));
        Assert.assertEquals("valid for " + aqlPath, expectedValid, result.valid());
    }

    @SneakyThrows
    private WebTemplate loadWebTemplate(final String resourcePath) {
        final InputStream is = getClass().getResourceAsStream(resourcePath);
        final String xml = IOUtils.toString(is);
        final var opt = TemplateDocument.Factory.parse(xml).getTemplate();
        return new OPTParser(opt).parse();
    }
}
