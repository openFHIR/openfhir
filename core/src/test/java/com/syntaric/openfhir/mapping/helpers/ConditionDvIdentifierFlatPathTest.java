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
 * An openehrCondition addressing a part of a DV_IDENTIFIER ({@code |type}, {@code |issuer}, {@code |id})
 * must keep that pipe attribute as the attribute flat path.
 * <p>
 * Those parts have no node of their own in the web template, so the AQL converter silently drops the
 * {@code |type} segment and returns the root flat path. Stripping {@code rootFlatPath + "/"} off that then
 * does nothing, and the evaluator used to receive a full flat path where it expected {@code |type} —
 * which made it exclude every identifier occurrence.
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
     * Documents the converter behaviour the fix works around: {@code |type} has no template node, so the
     * converter returns the root flat path unchanged.
     */
    @Test
    public void converterCannotResolvePipeAttributesOfADvIdentifier() {
        final AqlToFlatPathConverter.Result rootResult = converter.convert(IDENTIFIER_ROOT, null, webTemplate);
        final AqlToFlatPathConverter.Result attrResult =
                converter.convert(IDENTIFIER_ROOT + "/|type", null, webTemplate);

        Assert.assertEquals(EXPECTED_ROOT_FLAT, rootResult.flatPath());
        Assert.assertEquals("the converter drops the |type segment — hence the pass-through in amendCondition",
                            rootResult.flatPath(), attrResult.flatPath());
    }

    /** {@code |type} must survive amending as the attribute flat path, not be replaced by the root path. */
    @Test
    public void typePartIsKeptAsAttributeFlatPath() {
        final Condition amended = amend(conditionOn("|type"));

        Assert.assertEquals(EXPECTED_ROOT_FLAT, amended.getTargetRootFlatPath());
        Assert.assertEquals(List.of("|type"), amended.getTargetAttributesFlatPath());
    }

    /** The other DV_IDENTIFIER parts behave the same way. */
    @Test
    public void issuerAndIdPartsAreKeptAsAttributeFlatPaths() {
        Assert.assertEquals(List.of("|issuer"), amend(conditionOn("|issuer")).getTargetAttributesFlatPath());
        Assert.assertEquals(List.of("|id"), amend(conditionOn("|id")).getTargetAttributesFlatPath());
    }

    /** Several pipe parts on one condition are all passed through, preserving order. */
    @Test
    public void multiplePipeAttributesAreAllKept() {
        final Condition condition = new Condition()
                .withTargetRoot(IDENTIFIER_ROOT)
                .withTargetAttributes(List.of("|type", "|issuer"))
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
