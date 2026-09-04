package com.syntaric.openfhir.fc;

import com.syntaric.openfhir.fc.schema.Metadata;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.fc.schema.model.FhirConfig;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import com.syntaric.openfhir.fc.schema.model.Manual;
import com.syntaric.openfhir.fc.schema.model.ManualEntry;
import com.syntaric.openfhir.fc.schema.model.Mapping;
import com.syntaric.openfhir.fc.schema.model.OpenEhrConfig;
import com.syntaric.openfhir.fc.schema.model.With;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * https://github.com/openFHIR/openfhir/issues/88
 * <p>
 * Mirrors CLUSTER.multiple_coding_icd10gm.v1: three manual branches, each discriminated by an
 * openehrCondition whose targetRoot is "$openehrRoot". Every expanded toFHIR mapping must carry
 * its OWN branch's condition, resolved against the parent mapping's openehr path.
 */
public class Issue88OpenehrConditionExpansionTest {

    private static ManualEntry entry(final String path, final String value) {
        final ManualEntry manualEntry = new ManualEntry();
        manualEntry.setPath(path);
        manualEntry.setValue(value);
        return manualEntry;
    }

    private static Condition openehrCondition(final String criteria) {
        return new Condition()
                .withTargetRoot("$openehrRoot")
                .withTargetAttributes(List.of("defining_code/code_string"))
                .withOperator("one of")
                .withCriterias(criteria);
    }

    private static Manual branch(final String name, final String code, final String fhirValue) {
        final Manual manual = new Manual();
        manual.setName(name);
        manual.setOpenehr(List.of(
                entry("defining_code/terminology_id", "local"),
                entry("defining_code/code_string", code),
                entry("value", fhirValue)));
        manual.setFhir(List.of(
                entry("code", fhirValue),
                entry("display", fhirValue),
                entry("system", "http://fhir.de/CodeSystem/icd-10-gm-mehrfachcodierungs-kennzeichen")));
        manual.setOpenehrCondition(openehrCondition(code));
        return manual;
    }

    private static FhirConnectModel mehrfachcodierungModel() {
        final Mapping mehrfachcodierung = new Mapping()
                .withName("mehrfachcodierung")
                .withWith(new With()
                        .withFhir("$fhirRoot.value.as(Coding)")
                        .withOpenehr("$archetype/items[at0001]"))
                .withManual(List.of(
                        branch("manifestation", "at0003", "*"),
                        branch("aetiology", "at0002", "†"),
                        branch("additionalInformation", "at0004", "!")));

        final OpenEhrConfig openEhrConfig = new OpenEhrConfig();
        openEhrConfig.setArchetype("openEHR-EHR-CLUSTER.multiple_coding_icd10gm.v1");

        final Spec spec = new Spec();
        spec.setSystem(Spec.System.FHIR);
        spec.setVersion(Spec.Version.R4);
        spec.setOpenEhrConfig(openEhrConfig);
        spec.setFhirConfig(new FhirConfig()
                                   .withStructureDefinition("http://hl7.org/fhir/StructureDefinition/Coding"));

        final Metadata metadata = new Metadata();
        metadata.setName("CLUSTER.multiple_coding_icd10gm.v1");
        metadata.setVersion("0.0.1-alpha");

        final FhirConnectModel model = new FhirConnectModel();
        model.setSpec(spec);
        model.setMetadata(metadata);
        model.setMappings(List.of(mehrfachcodierung));
        return model;
    }

    @Test
    public void everyExpandedToFhirBranchKeepsItsOwnResolvedOpenehrCondition() {
        final OpenFhirFhirConnectModelMapper handled =
                new OpenFhirFhirConnectModelMapper().fromFhirConnectModelMapper(mehrfachcodierungModel());

        final List<Mapping> expanded = handled.getMappings().get(0).getFollowedBy().getMappings();

        final List<Mapping> toFhirMappings = expanded.stream()
                .filter(m -> FhirConnectConst.UNIDIRECTIONAL_TOFHIR.equals(m.getUnidirectional()))
                .toList();

        // 3 branches x 3 fhir paths (code, display, system)
        Assert.assertEquals(9, toFhirMappings.size());

        for (final Mapping toFhirMapping : toFhirMappings) {
            final Condition condition = toFhirMapping.getOpenehrCondition();
            Assert.assertNotNull("expanded toFHIR mapping " + toFhirMapping.getName()
                                         + " lost its openehrCondition", condition);
            Assert.assertEquals("$openehrRoot must be resolved to the parent mapping's openehr path",
                                "$archetype/items[at0001]", condition.getTargetRoot());
        }

        // Each branch must retain its OWN discriminating criteria - not the last branch's.
        assertBranchCriteria(toFhirMappings, "mehrfachcodierung.manifestation", "at0003");
        assertBranchCriteria(toFhirMappings, "mehrfachcodierung.aetiology", "at0002");
        assertBranchCriteria(toFhirMappings, "mehrfachcodierung.additionalInformation", "at0004");
    }

    private void assertBranchCriteria(final List<Mapping> toFhirMappings,
                                      final String branchName,
                                      final String expectedCriteria) {
        final List<Mapping> branchMappings = toFhirMappings.stream()
                .filter(m -> branchName.equals(m.getName()))
                .toList();
        Assert.assertEquals("expected 3 fhir paths for branch " + branchName, 3, branchMappings.size());
        for (final Mapping branchMapping : branchMappings) {
            Assert.assertEquals("branch " + branchName + " carries the wrong discriminator",
                                List.of(expectedCriteria),
                                branchMapping.getOpenehrCondition().getCriterias());
        }
    }
}
