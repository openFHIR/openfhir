package com.syntaric.openfhir.mapping.helpers;

import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;

/**
 * https://github.com/openFHIR/openfhir/issues/88
 * <p>
 * The Mehrfachcodierung kennzeichen of CLUSTER.multiple_coding_icd10gm.v1 is a DV_CODED_TEXT leaf,
 * so its openehrCondition ({@code targetAttribute: defining_code/code_string}) has to resolve to the
 * {@code |code} pipe attribute of the recurring diagnose occurrence. If either the root or the
 * attribute fails to resolve, the narrowing silently degrades to "match everything" and every manual
 * branch is emitted - which is what the issue reports.
 */
public class Issue88ConditionFlatPathTest {

    private static final String OPT = "/kds/diagnose/KDS_Diagnose.opt";

    private static final String MULTIPLE_CODING_ROOT =
            "openEHR-EHR-EVALUATION.problem_diagnosis.v1"
                    + "/data[at0001]/items[openEHR-EHR-CLUSTER.multiple_coding_icd10gm.v1]"
                    + "/items[at0001]";

    private static final String EXPECTED_ROOT_FLAT =
            "diagnose/diagnose[n]/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen";

    private WebTemplate webTemplate;
    private AqlToFlatPathConverter converter;

    @SneakyThrows
    @Before
    public void setUp() {
        final String serialized = IOUtils.toString(getClass().getResourceAsStream(OPT));
        final OPERATIONALTEMPLATE operationaltemplate = TemplateDocument.Factory.parse(serialized).getTemplate();
        webTemplate = new OPTParser(operationaltemplate).parse();
        converter = new AqlToFlatPathConverter(new OpenFhirStringUtils());
    }

    /**
     * The narrowing root must be the recurring diagnose occurrence path, so that each diagnose
     * entry is evaluated independently.
     */
    @Test
    public void conditionRootResolvesToRecurringDiagnosePath() {
        final AqlToFlatPathConverter.Result rootResult = converter.convert(MULTIPLE_CODING_ROOT, webTemplate);

        Assert.assertTrue("condition root must resolve against the template", rootResult.valid());
        Assert.assertEquals(EXPECTED_ROOT_FLAT, rootResult.flatPath());
    }

    /**
     * {@code defining_code/code_string} must end up as the {@code |code} pipe attribute, matching the
     * key actually present in the flat composition.
     */
    @Test
    public void conditionAttributeResolvesToCodePipeAttribute() {
        final Condition amended = new HelpersCreator(null, converter).amendCondition(
                new Condition()
                        .withTargetRoot(MULTIPLE_CODING_ROOT)
                        .withTargetAttributes(List.of("defining_code/code_string"))
                        .withOperator("one of")
                        .withCriterias("!"),
                null, "openEHR-EHR-EVALUATION.problem_diagnosis.v1", null, null, false, null, webTemplate);

        Assert.assertEquals(EXPECTED_ROOT_FLAT, amended.getTargetRootFlatPath());
        Assert.assertEquals("the condition attribute must resolve to the |code pipe attribute",
                            List.of("|code"), amended.getTargetAttributesFlatPath());
    }
}
