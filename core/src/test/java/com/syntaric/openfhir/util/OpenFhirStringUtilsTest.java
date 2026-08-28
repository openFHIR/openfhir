package com.syntaric.openfhir.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class OpenFhirStringUtilsTest {

    @Test
    public void joinValuesThatAreOne() {
        final List<String> toJoin = Arrays.asList(
                "growth_chart/body_weight/any_event:1/weight|unit",
                "growth_chart/body_weight/any_event:1/comment",
                "growth_chart/body_weight/any_event:1/state_of_dress|code",
                "growth_chart/body_weight/any_event:1/state_of_dress|terminology",
                "growth_chart/body_weight/any_event:1/state_of_dress|value",
                "growth_chart/body_weight/any_event:1/confounding_factors:0",
                "growth_chart/body_weight/any_event:1/time",
                "growth_chart/body_weight/any_event:2/weight|unit",
                "growth_chart/body_weight/any_event:2/weight|magnitude",
                "growth_chart/body_weight/any_event:2/comment",
                "growth_chart/body_weight/any_event:2/state_of_dress|code",
                "growth_chart/body_weight/any_event:2/state_of_dress|value",
                "growth_chart/body_weight/any_event:2/state_of_dress|terminology",
                "growth_chart/body_weight/any_event:2/confounding_factors:0",
                "growth_chart/body_weight/any_event:2/time",
                "growth_chart/body_weight/any_event:2/width",
                "growth_chart/body_weight/any_event:2/math_function|terminology",
                "growth_chart/body_weight/any_event:2/math_function|code",
                "growth_chart/body_weight/any_event:2/math_function|value",
                "growth_chart/body_weight/language|code",
                "growth_chart/body_weight/language|terminology",
                "growth_chart/body_weight/encoding|code",
                "growth_chart/body_weight/encoding|terminology",
                "growth_chart/body_weight/_work_flow_id|id",
                "growth_chart/body_weight/_work_flow_id|id_scheme",
                "growth_chart/body_weight/_work_flow_id|namespace",
                "growth_chart/body_weight/_work_flow_id|type",
                "growth_chart/body_weight/_guideline_id|id"
        );
        final Map<String, List<String>> stringListMap = new OpenFhirStringUtils().joinValuesThatAreOne(toJoin);
        Assert.assertEquals(3, stringListMap.get("growth_chart/body_weight/any_event:1/state_of_dress").size());
        Assert.assertEquals("growth_chart/body_weight/any_event:1/state_of_dress|code",
                            stringListMap.get("growth_chart/body_weight/any_event:1/state_of_dress").get(0));
        Assert.assertEquals("growth_chart/body_weight/any_event:1/state_of_dress|terminology",
                            stringListMap.get("growth_chart/body_weight/any_event:1/state_of_dress").get(1));
        Assert.assertEquals("growth_chart/body_weight/any_event:1/state_of_dress|value",
                            stringListMap.get("growth_chart/body_weight/any_event:1/state_of_dress").get(2));
        Assert.assertEquals(3, stringListMap.get("growth_chart/body_weight/any_event:2/math_function").size());
        Assert.assertEquals(2, stringListMap.get("growth_chart/body_weight/encoding").size());
        Assert.assertEquals(1, stringListMap.get("growth_chart/body_weight/any_event:1/confounding_factors:0").size());
    }


    @Test
    public void joinValuesThatAreOne_oneContainsTheOther() {
        final List<String> toJoin = Arrays.asList(
                "diagnose/diagnose:0/klinisch_relevanter_zeitraum_zeitpunkt_der_genesung",
                "diagnose/diagnose:0/klinischer_status/diagnostic_status|code",
                "diagnose/diagnose:0/klinischer_status/diagnostic_status|terminology",
                "diagnose/diagnose:0/klinischer_status/diagnostic_status|value",
                "diagnose/diagnose:0/klinischer_status/klinischer_status|terminology",
                "diagnose/diagnose:0/klinischer_status/klinischer_status|code",
                "diagnose/diagnose:0/klinischer_status/klinischer_status|value",
                "diagnose/diagnose:0/klinischer_status/klinischer_status2|value",
                "diagnose/diagnose:0/klinischer_status/klinischer_status2|terminology",
                "diagnose/diagnose:0/klinischer_status/klinischer_status2|code",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle|code",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle|terminology",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle|value",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle2|value",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle2|code",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle2|terminology",
                "diagnose/diagnose:0/diagnosesicherheit|value",
                "diagnose/diagnose:0/diagnosesicherheit|terminology",
                "diagnose/diagnose:0/diagnosesicherheit|code",
                "diagnose/diagnose:0/diagnosesicherheit2|value",
                "diagnose/diagnose:0/diagnosesicherheit2|code",
                "diagnose/diagnose:0/diagnosesicherheit2|terminology",
                "diagnose/diagnose:0/diagnoseerläuterung",
                "diagnose/diagnose:0/letztes_dokumentationsdatum",
                "diagnose/diagnose:0/language|code",
                "diagnose/diagnose:0/language|terminology"
        );
        final Map<String, List<String>> stringListMap = new OpenFhirStringUtils().joinValuesThatAreOne(
                toJoin);
        Assert.assertEquals(3, stringListMap.get("diagnose/diagnose:0/klinischer_status/klinischer_status").size());

        final JsonObject flatJsonObject = new JsonObject();
        toJoin.forEach(tj -> flatJsonObject.add(tj, new JsonPrimitive("random")));

        flatJsonObject.add("diagnose/diagnose:0/lebensphase/ende|code", new JsonPrimitive("44"));
        flatJsonObject.add("diagnose/diagnose:0/lebensphase/ende|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de' available"));
        flatJsonObject.add("diagnose/diagnose:0/lebensphase/ende|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de"));
        flatJsonObject.add("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|value",
                           new JsonPrimitive("†"));
        flatJsonObject.add("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|code",
                           new JsonPrimitive("at0002"));
        flatJsonObject.add("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|terminology",
                           new JsonPrimitive("local"));
        flatJsonObject.add("diagnose/diagnose:0/klinisch_relevanter_zeitraum_zeitpunkt_der_genesung",
                           new JsonPrimitive("2022-02-03T04:05:06"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnostic_status|code", new JsonPrimitive("at0016"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnostic_status|terminology",
                           new JsonPrimitive("local"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnostic_status|value",
                           new JsonPrimitive("Preliminary"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status|terminology",
                           new JsonPrimitive("local"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status|code", new JsonPrimitive("at0026"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status|value",
                           new JsonPrimitive("Active"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status2|value",
                           new JsonPrimitive("Active"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status2|terminology",
                           new JsonPrimitive("local"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status2|code",
                           new JsonPrimitive("at0026"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle|code", new JsonPrimitive("42"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/diagnosis-role"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/diagnosis-role' available"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle2|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/diagnosis-role' available"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle2|code", new JsonPrimitive("42"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle2|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/diagnosis-role"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT' available"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit|code", new JsonPrimitive("42"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit2|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT' available"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit2|code", new JsonPrimitive("42"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit2|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT"));
        flatJsonObject.add("diagnose/diagnose:0/diagnoseerläuterung", new JsonPrimitive("Lorem ipsum"));
        flatJsonObject.add("diagnose/diagnose:0/letztes_dokumentationsdatum", new JsonPrimitive("2022-02-03T04:05:06"));
        flatJsonObject.add("diagnose/diagnose:0/language|code", new JsonPrimitive("en"));

        final List<String> matchingEntries = new OpenFhirStringUtils().getAllEntriesThatMatch(
                new OpenFhirStringUtils().addRegexPatternToSimplifiedFlatFormat(
                        "diagnose/diagnose/klinischer_status/klinischer_status"),
                flatJsonObject);
        Assert.assertEquals(3, matchingEntries.size());
    }

    @Test
    public void getAllEntriesThatMatchIgnoringPipe() {
        final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();

        final List<String> toJoin = Arrays.asList(
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|value",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|code",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|terminology",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|terminology",
                "stationärer_versorgungsfall/aufnahmedaten/abc|abc",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|value",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|code",
                "stationärer_versorgungsfall/aufnahmedaten/kennung_vor_der_aufnahme",
                "stationärer_versorgungsfall/aufnahmedaten/datum_uhrzeit_der_aufnahme",
                "stationärer_versorgungsfall/aufnahmedaten/vorheriger_patientenstandort_vor_aufnahme/campus"
        );

        final JsonObject flatJsonObject = new JsonObject();
        toJoin.forEach(tj -> flatJsonObject.add(tj, new JsonPrimitive(tj)));

        Assert.assertEquals("stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|terminology",
                            openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(
                                    "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|terminology",
                                    flatJsonObject).get(0));
        Assert.assertEquals("stationärer_versorgungsfall/aufnahmedaten/abc|abc",
                            openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(
                                    "stationärer_versorgungsfall/aufnahmedaten/abc", flatJsonObject).get(0));
        Assert.assertTrue(openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(
                "stationärer_versorgungsfall/aufnahmedaten/abc/cde", flatJsonObject).isEmpty());
    }

    /**
     * Pins how this matcher behaves when the flat entry carries a repetition index that the lookup
     * path does not.
     * <p>
     * An {@code openehrCondition} with {@code operator: "not empty"} resolves its targetAttribute to
     * an index-free flat path (for example {@code medication_safety/safety_override}) and looks it up
     * against the real composition, where a {@code 0..*} node is written with an index
     * ({@code medication_safety/safety_override:0/override_reason:0}). Because the comparison is a
     * {@code startsWith}, the index-free prefix DOES match — so a "not empty" condition on a
     * repeating node is not, on its own, unable to see that node.
     * <p>
     * The second half of the test pins the other direction: a lookup path that itself carries an
     * index only matches an entry with the same index, which is what makes an index-free
     * targetAttribute the right thing for the condition evaluator to construct.
     */
    @Test
    public void getAllEntriesThatMatchIgnoringPipe_repeatingNodes() {
        final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();

        final List<String> flatEntries = Arrays.asList(
                // 0..* cluster, indexed, with a 0..* leaf inside it
                "prescription/medication_order:0/order:0/medication_safety/safety_override:0/override_reason:0",
                "prescription/medication_order:0/order:0/medication_safety/safety_override:0/overriden_safety_advice",
                // 0..1 cluster, not indexed
                "prescription/medication_order:0/order:0/medication_safety/total_daily_effective_dose/purpose",
                // unrelated sibling that must never match
                "prescription/medication_order:0/order:0/medication_safety/exceptional_safety_override"
        );

        final JsonObject flatJsonObject = new JsonObject();
        flatEntries.forEach(fe -> flatJsonObject.add(fe, new JsonPrimitive(fe)));

        // An index-free prefix matches an indexed entry: startsWith sees safety_override:0/... as
        // starting with safety_override.
        Assert.assertEquals(
                2,
                openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(
                        "prescription/medication_order:0/order:0/medication_safety/safety_override",
                        flatJsonObject).size());

        // The same holds for a non-repeating cluster.
        Assert.assertEquals(
                1,
                openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(
                        "prescription/medication_order:0/order:0/medication_safety/total_daily_effective_dose",
                        flatJsonObject).size());

        // An explicitly indexed lookup path matches only that occurrence.
        Assert.assertEquals(
                2,
                openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(
                        "prescription/medication_order:0/order:0/medication_safety/safety_override:0",
                        flatJsonObject).size());
        Assert.assertTrue(
                openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(
                        "prescription/medication_order:0/order:0/medication_safety/safety_override:1",
                        flatJsonObject).isEmpty());

        // A path that does not exist at all matches nothing.
        Assert.assertTrue(
                openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(
                        "prescription/medication_order:0/order:0/medication_safety/no_such_node",
                        flatJsonObject).isEmpty());
    }

    @Test
    public void getCastType_primitivesGetTypeSuffix() {
        final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();

        // primitives whose HAPI model class carries a `Type` suffix
        Assert.assertEquals("IntegerType", openFhirStringUtils.getCastType("Observation.value.as(Integer)"));
        Assert.assertEquals("DecimalType", openFhirStringUtils.getCastType("Observation.value.as(Decimal)"));
        Assert.assertEquals("DateType", openFhirStringUtils.getCastType("Patient.deceased.as(Date)"));
        Assert.assertEquals("UriType", openFhirStringUtils.getCastType("Observation.value.as(Uri)"));
        Assert.assertEquals("CodeType", openFhirStringUtils.getCastType("Observation.value.as(Code)"));
        Assert.assertEquals("PositiveIntType", openFhirStringUtils.getCastType("Observation.value.as(PositiveInt)"));
        Assert.assertEquals("Base64BinaryType", openFhirStringUtils.getCastType("Observation.value.as(Base64Binary)"));

        // previously the only mapped ones, must keep working
        Assert.assertEquals("BooleanType", openFhirStringUtils.getCastType("Patient.deceased.as(Boolean)"));
        Assert.assertEquals("DateTimeType", openFhirStringUtils.getCastType("Patient.deceased.as(DateTime)"));
        Assert.assertEquals("TimeType", openFhirStringUtils.getCastType("Observation.value.as(Time)"));
        Assert.assertEquals("StringType", openFhirStringUtils.getCastType("Observation.value.as(String)"));
    }

    @Test
    public void getCastType_complexTypesAreLeftUnchanged() {
        final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();

        // complex types are already named exactly like their HAPI model class, no suffix may be added
        Assert.assertEquals("Quantity", openFhirStringUtils.getCastType("Observation.value.as(Quantity)"));
        Assert.assertEquals("CodeableConcept",
                openFhirStringUtils.getCastType("Observation.value.as(CodeableConcept)"));
        Assert.assertEquals("Period", openFhirStringUtils.getCastType("Observation.effective.as(Period)"));
        Assert.assertEquals("Ratio", openFhirStringUtils.getCastType("Observation.value.as(Ratio)"));

        // an already suffixed cast type must not be suffixed twice
        Assert.assertEquals("StringType", openFhirStringUtils.getCastType("Observation.value.as(StringType)"));

        // no cast in the path at all
        Assert.assertNull(openFhirStringUtils.getCastType("Observation.value"));
    }
}