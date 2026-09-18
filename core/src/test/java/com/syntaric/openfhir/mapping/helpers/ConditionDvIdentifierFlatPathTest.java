package com.syntaric.openfhir.mapping.helpers;

import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
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
 * An openehrCondition addressing a part of a DV_IDENTIFIER must resolve to the pipe attribute that part
 * has in the flat format.
 * <p>
 * Conditions address openEHR with RM paths, so the parts are written {@code type} / {@code issuer} /
 * {@code id} (or {@code value/type}), the same way a DV_CODED_TEXT is narrowed on
 * {@code defining_code/code_string}. They have no node of their own in the web template, so the AQL
 * converter silently drops the segment and returns the parent's flat path. Stripping
 * {@code rootFlatPath + "/"} off that then does nothing, and the evaluator used to receive a full flat
 * path where it expected {@code |type} — which made it exclude every identifier occurrence.
 */
public class ConditionDvIdentifierFlatPathTest {

    private static final String OPT = "/kds/laborauftrag/KDS_Laborauftrag.opt";

    /** The identifier ELEMENT (DV_IDENTIFIER valued) of the Einsender organisation cluster. */
    private static final String IDENTIFIER_ROOT =
            "openEHR-EHR-INSTRUCTION.service_request.v1"
                    + "/protocol[at0008]/items[openEHR-EHR-CLUSTER.organisation.v1]/items[at0003]";

    private static final String EXPECTED_ROOT_FLAT = "leistungsanforderung/laborleistung/einsender/identifier";

    private WebTemplate webTemplate;
    private HelpersCreator helpersCreator;
    private AqlToFlatPathConverter converter;

    @SneakyThrows
    @Before
    public void setUp() {
        final String serialized = IOUtils.toString(getClass().getResourceAsStream(OPT));
        final OPERATIONALTEMPLATE operationaltemplate = TemplateDocument.Factory.parse(serialized).getTemplate();
        webTemplate = new OPTParser(operationaltemplate).parse();
        converter = new AqlToFlatPathConverter(new OpenFhirStringUtils(), new OpenFhirMapperUtils());
        helpersCreator = new HelpersCreator(null, converter);
    }

    private Condition amend(final Condition condition) {
        return helpersCreator.amendCondition(condition, null,
                                             "openEHR-EHR-INSTRUCTION.service_request.v1", null, null,
                                             false, null, webTemplate);
    }

    private Condition conditionOn(final String targetAttribute) {
        return new Condition()
                .withTargetRoot(IDENTIFIER_ROOT)
                .withTargetAttributes(List.of(targetAttribute))
                .withOperator("one of")
                .withCriterias("urn:oid:1.2.752.29.4.19");
    }

    /**
     * Documents the converter behaviour the fix works around: the RM attributes of a DV_IDENTIFIER have
     * no template node, so the converter returns the parent's flat path unchanged.
     */
    @Test
    public void converterCannotResolveRmAttributesOfADvIdentifier() {
        final AqlToFlatPathConverter.Result rootResult = converter.convert(IDENTIFIER_ROOT, null, webTemplate);

        Assert.assertEquals(EXPECTED_ROOT_FLAT, rootResult.flatPath());
        for (final String rmAttribute : List.of("type", "issuer", "id", "assigner")) {
            Assert.assertEquals("the converter drops the " + rmAttribute
                                        + " segment — hence the rewrite in amendCondition",
                                rootResult.flatPath(),
                                converter.convert(IDENTIFIER_ROOT + "/" + rmAttribute, null, webTemplate).flatPath());
        }
    }

    /**
     * The RM attribute {@code type} must resolve to the {@code |type} pipe attribute, the same way
     * {@code defining_code/code_string} resolves to {@code |code}.
     */
    @Test
    public void typePartResolvesToPipeAttribute() {
        final Condition amended = amend(conditionOn("type"));

        Assert.assertEquals(EXPECTED_ROOT_FLAT, amended.getTargetRootFlatPath());
        Assert.assertEquals(List.of("|type"), amended.getTargetAttributesFlatPath());
    }

    /** The other DV_IDENTIFIER parts behave the same way. */
    @Test
    public void issuerIdAndAssignerPartsResolveToPipeAttributes() {
        Assert.assertEquals(List.of("|issuer"), amend(conditionOn("issuer")).getTargetAttributesFlatPath());
        Assert.assertEquals(List.of("|id"), amend(conditionOn("id")).getTargetAttributesFlatPath());
        Assert.assertEquals(List.of("|assigner"), amend(conditionOn("assigner")).getTargetAttributesFlatPath());
    }

    /** Spelling the part out through the ELEMENT's value attribute resolves identically. */
    @Test
    public void partAddressedThroughTheValueAttributeResolvesToTheSamePipeAttribute() {
        Assert.assertEquals(List.of("|type"), amend(conditionOn("value/type")).getTargetAttributesFlatPath());
        Assert.assertEquals(List.of("|issuer"), amend(conditionOn("value/issuer")).getTargetAttributesFlatPath());
    }

    /** The flat pipe spelling stays accepted, for mappings that already use it. */
    @Test
    public void flatPipeSpellingIsStillAccepted() {
        Assert.assertEquals(List.of("|type"), amend(conditionOn("|type")).getTargetAttributesFlatPath());
        Assert.assertEquals(List.of("|issuer"), amend(conditionOn("|issuer")).getTargetAttributesFlatPath());
    }

    /** Several parts on one condition are all resolved, preserving order. */
    @Test
    public void multiplePartsAreAllResolved() {
        final Condition condition = new Condition()
                .withTargetRoot(IDENTIFIER_ROOT)
                .withTargetAttributes(List.of("type", "issuer"))
                .withOperator("one of")
                .withCriterias("urn:oid:1.2.752.29.4.19");

        Assert.assertEquals(List.of("|type", "|issuer"), amend(condition).getTargetAttributesFlatPath());
    }

    /**
     * Non-pipe attributes must keep going through the converter — the pass-through may not regress the
     * DV_CODED_TEXT handling.
     */
    @Test
    public void nonPipeAttributesStillGoThroughTheConverter() {
        final Condition condition = new Condition()
                .withTargetRoot("openEHR-EHR-INSTRUCTION.service_request.v1"
                                        + "/protocol[at0008]/items[openEHR-EHR-CLUSTER.organisation.v1]/items[at0001]")
                .withTargetAttributes(List.of("value"))
                .withOperator("one of")
                .withCriterias("Some organisation");

        final Condition amended = amend(condition);

        Assert.assertEquals("leistungsanforderung/laborleistung/einsender/namenszeile",
                            amended.getTargetRootFlatPath());
        Assert.assertEquals(List.of("value"), amended.getTargetAttributesFlatPath());
    }
}
