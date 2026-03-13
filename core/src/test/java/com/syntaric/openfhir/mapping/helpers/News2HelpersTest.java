package com.syntaric.openfhir.mapping.helpers;

import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

public class News2HelpersTest extends GenericHelpersTest {

    final String MODEL_MAPPINGS = "/news2/";
    final String CONTEXT_MAPPING = "/news2/NEWS2_Context_Mapping.context.yaml";
    final String HELPER_LOCATION = "/news2/";
    final String OPT = "NEWS2 Encounter Parent.opt";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
    }

    @Test
    public void assertNews2Helpers() {
        final String templateId = context.getContext().getTemplate().getId();
        final String start = context.getContext().getStart();

        final Map<String, List<MappingHelper>> allHelpersPerArchetype = helpersCreator.constructHelpers(templateId, start,
                                                                                                        context.getContext().getArchetypes(),
                                                                                                        webTemplate);
        final List<MappingHelper> allHelpers = allHelpersPerArchetype.get("openEHR-EHR-COMPOSITION.encounter.v1");

        // 7 top-level parent mappings: news2Parent, acvpuParent, bloodPressureParent, bodyTemperatureParent,
        //                              pulseParent, respirationParent, pulseOximetryParent
        Assert.assertEquals(7, allHelpers.size());

        // --- news2Parent → iterateNews2 → news2Slot → [leaf helpers] ---
        final MappingHelper news2Parent = allHelpers.get(0);
        Assert.assertEquals("news2Parent", news2Parent.getMappingName());
        Assert.assertEquals("entry", news2Parent.getFhir());
        Assert.assertEquals("Bundle.entry", news2Parent.getFullFhirPath());

        final MappingHelper iterateNews2 = news2Parent.getChildren().get(0);
        Assert.assertEquals("iterateNews2", iterateNews2.getMappingName());

        final MappingHelper news2Slot = iterateNews2.getChildren().get(0);
        Assert.assertEquals("news2Slot", news2Slot.getMappingName());
        Assert.assertTrue(news2Slot.isHasSlot());

        final List<MappingHelper> news2LeafHelpers = news2Slot.getChildren();
        // 9 leaf helpers from NEWS2_Model_Mapping: nationalEarlyWarningScoreNEWS2, time, rrs, spos1, airOrOxygenScore,
        //                                          systolic, consciousness, temperature, pulse
        Assert.assertEquals(9, news2LeafHelpers.size());

        final MappingHelper news2Score = news2LeafHelpers.get(0);
        Assert.assertEquals("nationalEarlyWarningScoreNEWS2", news2Score.getMappingName());
        Assert.assertEquals("value.as(Quantity)", news2Score.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().value.as(Quantity)", news2Score.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.news2.v1/data[at0001]/events[at0002]/data[at0003]/items[at0028]",
                            news2Score.getOpenEhr());
        Assert.assertNull(news2Score.getFhirConditions());
        Assert.assertEquals("news2_encounter_parent/national_early_warning_score_2_news2/total_score",
                            news2Score.getFullOpenEhrFlatPath());
        Assert.assertEquals("DV_COUNT", news2Score.getDetectedType());

        final MappingHelper news2Time = news2LeafHelpers.get(1);
        Assert.assertEquals("time", news2Time.getMappingName());
        Assert.assertEquals("effective", news2Time.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().effective", news2Time.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.news2.v1/data[at0001]/events[at0002]/time",
                            news2Time.getOpenEhr());
        Assert.assertNull(news2Time.getFhirConditions());
        Assert.assertEquals("news2_encounter_parent/national_early_warning_score_2_news2/time",
                            news2Time.getFullOpenEhrFlatPath());
        Assert.assertEquals("DV_DATE_TIME", news2Time.getDetectedType());

        final MappingHelper rrs = news2LeafHelpers.get(2);
        Assert.assertEquals("rrs", rrs.getMappingName());
        Assert.assertEquals("component", rrs.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component", rrs.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.news2.v1/data[at0001]/events[at0002]/data[at0003]",
                            rrs.getOpenEhr());
        Assert.assertNotNull(rrs.getFhirConditions());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component", rrs.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("code.coding.code", rrs.getFhirConditions().get(0).getTargetAttribute());
        Assert.assertEquals("news2_encounter_parent/national_early_warning_score_2_news2",
                            rrs.getFullOpenEhrFlatPath());

        final MappingHelper spos1 = news2LeafHelpers.get(3);
        Assert.assertEquals("spos1", spos1.getMappingName());
        Assert.assertNotNull(spos1.getFhirConditions());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component", spos1.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("news2_encounter_parent/national_early_warning_score_2_news2",
                            spos1.getFullOpenEhrFlatPath());

        final MappingHelper airOrOxygen = news2LeafHelpers.get(4);
        Assert.assertEquals("airOrOxygenScore", airOrOxygen.getMappingName());
        Assert.assertNotNull(airOrOxygen.getFhirConditions());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component", airOrOxygen.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("news2_encounter_parent/national_early_warning_score_2_news2",
                            airOrOxygen.getFullOpenEhrFlatPath());

        final MappingHelper systolic = news2LeafHelpers.get(5);
        Assert.assertEquals("systolic", systolic.getMappingName());
        Assert.assertNotNull(systolic.getFhirConditions());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component", systolic.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("news2_encounter_parent/national_early_warning_score_2_news2",
                            systolic.getFullOpenEhrFlatPath());

        final MappingHelper consciousness = news2LeafHelpers.get(6);
        Assert.assertEquals("consciousness", consciousness.getMappingName());
        Assert.assertNotNull(consciousness.getFhirConditions());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component", consciousness.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("news2_encounter_parent/national_early_warning_score_2_news2",
                            consciousness.getFullOpenEhrFlatPath());

        final MappingHelper temperature = news2LeafHelpers.get(7);
        Assert.assertEquals("temperature", temperature.getMappingName());
        Assert.assertNotNull(temperature.getFhirConditions());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component", temperature.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("news2_encounter_parent/national_early_warning_score_2_news2",
                            temperature.getFullOpenEhrFlatPath());

        final MappingHelper pulse = news2LeafHelpers.get(8);
        Assert.assertEquals("pulse", pulse.getMappingName());
        Assert.assertNotNull(pulse.getFhirConditions());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component", pulse.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("news2_encounter_parent/national_early_warning_score_2_news2",
                            pulse.getFullOpenEhrFlatPath());

        // --- acvpuParent → iterateAcvpu → acvpuSlot → [ACVPU] ---
        final MappingHelper acvpuParent = allHelpers.get(1);
        Assert.assertEquals("acvpuParent", acvpuParent.getMappingName());

        final MappingHelper iterateAcvpu = acvpuParent.getChildren().get(0);
        Assert.assertEquals("iterateAcvpu", iterateAcvpu.getMappingName());

        final MappingHelper acvpuSlot = iterateAcvpu.getChildren().get(0);
        Assert.assertEquals("acvpuSlot", acvpuSlot.getMappingName());
        Assert.assertTrue(acvpuSlot.isHasSlot());

        final MappingHelper acvpu = acvpuSlot.getChildren().get(0);
        Assert.assertEquals("ACVPU", acvpu.getMappingName());
        Assert.assertEquals("value", acvpu.getFhir());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.acvpu.v1/data[at0001]/events[at0002]/data[at0003]/items[at0004]",
                            acvpu.getOpenEhr());
        Assert.assertNull(acvpu.getFhirConditions());
        Assert.assertEquals("news2_encounter_parent/acvpu_scale/any_point_in_time_event[n]/acvpu",
                            acvpu.getFullOpenEhrFlatPath());
        Assert.assertEquals("DV_CODED_TEXT", acvpu.getDetectedType());

        // --- bloodPressureParent → iterateBloodPressure → bloodPressureSlot → [4 leaf helpers] ---
        final MappingHelper bloodPressureParent = allHelpers.get(2);
        Assert.assertEquals("bloodPressureParent", bloodPressureParent.getMappingName());

        final MappingHelper iterateBloodPressure = bloodPressureParent.getChildren().get(0);
        Assert.assertEquals("iterateBloodPressure", iterateBloodPressure.getMappingName());

        final MappingHelper bloodPressureSlot = iterateBloodPressure.getChildren().get(0);
        Assert.assertEquals("bloodPressureSlot", bloodPressureSlot.getMappingName());
        Assert.assertTrue(bloodPressureSlot.isHasSlot());

        final List<MappingHelper> bpLeafHelpers = bloodPressureSlot.getChildren();
        Assert.assertEquals(5, bpLeafHelpers.size());

        final MappingHelper bpSystolic = bpLeafHelpers.get(0);
        Assert.assertEquals("componentSystolic", bpSystolic.getMappingName());
        Assert.assertEquals("component", bpSystolic.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component", bpSystolic.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]/data[at0003]",
                            bpSystolic.getOpenEhr());
        Assert.assertNotNull(bpSystolic.getFhirConditions());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component", bpSystolic.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("code.coding.code", bpSystolic.getFhirConditions().get(0).getTargetAttribute());
        Assert.assertEquals("news2_encounter_parent/blood_pressure/any_event[n]",
                            bpSystolic.getFullOpenEhrFlatPath());

        final MappingHelper bpDiastolic = bpLeafHelpers.get(1);
        Assert.assertEquals("componentDiastolic", bpDiastolic.getMappingName());
        Assert.assertEquals("component", bpDiastolic.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component", bpDiastolic.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]/data[at0003]",
                            bpDiastolic.getOpenEhr());
        Assert.assertNotNull(bpDiastolic.getFhirConditions());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component", bpDiastolic.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("news2_encounter_parent/blood_pressure/any_event[n]",
                            bpDiastolic.getFullOpenEhrFlatPath());

        final MappingHelper bodySite = bpLeafHelpers.get(2);
        Assert.assertEquals("bodySiteLocationOfMeasurement", bodySite.getMappingName());
        Assert.assertEquals("bodySite", bodySite.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().bodySite", bodySite.getFullFhirPath());
        Assert.assertNull(bodySite.getFhirConditions());
        Assert.assertEquals("news2_encounter_parent/blood_pressure/location_of_measurement",
                            bodySite.getFullOpenEhrFlatPath());
        Assert.assertEquals("DV_CODED_TEXT", bodySite.getDetectedType());

        final MappingHelper clinicalInterpretation = bpLeafHelpers.get(3);
        Assert.assertEquals("clinicalInterpretation", clinicalInterpretation.getMappingName());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component.interpretation.text",
                            clinicalInterpretation.getFullFhirPath());
        Assert.assertEquals("component.interpretation.text", clinicalInterpretation.getFhir());
        Assert.assertNull(clinicalInterpretation.getFhirConditions());
        Assert.assertEquals("news2_encounter_parent/blood_pressure/any_event[n]",
                            clinicalInterpretation.getFullOpenEhrFlatPath());

        // --- bodyTemperatureParent → iterateBodyTemperature → bodyTemperatureSlot → [value] ---
        final MappingHelper bodyTemperatureParent = allHelpers.get(3);
        Assert.assertEquals("bodyTemperatureParent", bodyTemperatureParent.getMappingName());

        final MappingHelper bodyTemperatureSlot = bodyTemperatureParent.getChildren().get(0).getChildren().get(0);
        Assert.assertEquals("bodyTemperatureSlot", bodyTemperatureSlot.getMappingName());

        final MappingHelper bodyTemp = bodyTemperatureSlot.getChildren().get(0);
        Assert.assertEquals("value", bodyTemp.getMappingName());
        Assert.assertEquals("value", bodyTemp.getFhir());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.body_temperature.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004]",
                            bodyTemp.getOpenEhr());
        Assert.assertEquals("news2_encounter_parent/body_temperature/any_event[n]/temperature",
                            bodyTemp.getFullOpenEhrFlatPath());
        Assert.assertEquals("DV_QUANTITY", bodyTemp.getDetectedType());

        // --- pulseParent → iteratePulse → pulseSlot → [value] ---
        final MappingHelper pulseParent = allHelpers.get(4);
        Assert.assertEquals("pulseParent", pulseParent.getMappingName());

        final MappingHelper pulseSlot = pulseParent.getChildren().get(0).getChildren().get(0);
        Assert.assertEquals("pulseSlot", pulseSlot.getMappingName());

        final MappingHelper pulseValue = pulseSlot.getChildren().get(0);
        Assert.assertEquals("value", pulseValue.getMappingName());
        Assert.assertEquals("value", pulseValue.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().value", pulseValue.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.pulse.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004]",
                            pulseValue.getOpenEhr());
        Assert.assertEquals("news2_encounter_parent/pulse_heart_beat/any_event[n]/rate",
                            pulseValue.getFullOpenEhrFlatPath());

        // --- respirationParent → iterateRespiration → respirationSlot → [value] ---
        final MappingHelper respirationParent = allHelpers.get(5);
        Assert.assertEquals("respirationParent", respirationParent.getMappingName());

        final MappingHelper respirationSlot = respirationParent.getChildren().get(0).getChildren().get(0);
        Assert.assertEquals("respirationSlot", respirationSlot.getMappingName());

        final MappingHelper respirationValue = respirationSlot.getChildren().get(0);
        Assert.assertEquals("value", respirationValue.getMappingName());
        Assert.assertEquals("value", respirationValue.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().value", respirationValue.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.respiration.v2/data[at0001]/events[at0002]/data[at0003]/items[at0004]",
                            respirationValue.getOpenEhr());
        Assert.assertEquals("news2_encounter_parent/respiration/any_event[n]/rate",
                            respirationValue.getFullOpenEhrFlatPath());

        // --- pulseOximetryParent → iteratePulseOximetry → pulseOximetrySlot → [value] ---
        final MappingHelper pulseOximetryParent = allHelpers.get(6);
        Assert.assertEquals("pulseOximetryParent", pulseOximetryParent.getMappingName());

        final MappingHelper pulseOximetrySlot = pulseOximetryParent.getChildren().get(0).getChildren().get(0);
        Assert.assertEquals("pulseOximetrySlot", pulseOximetrySlot.getMappingName());

        final MappingHelper spo2Value = pulseOximetrySlot.getChildren().get(0);
        Assert.assertEquals("value", spo2Value.getMappingName());
        Assert.assertEquals("value", spo2Value.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().value", spo2Value.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.pulse_oximetry.v1/data[at0001]/events[at0002]/data[at0003]/items[at0006]",
                            spo2Value.getOpenEhr());
        Assert.assertEquals("news2_encounter_parent/pulse_oximetry/any_event[n]/spo",
                            spo2Value.getFullOpenEhrFlatPath());
    }

}
