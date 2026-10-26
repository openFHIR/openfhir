package com.syntaric.openfhir.mapping.helpers;

import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** A mapping's {@code with.type} picks among the types the template allows; it never adds one. */
public class RmTypesTest {

    private static final List<String> CHOICE = List.of("DV_TEXT", "DV_QUANTITY", "DV_COUNT");

    @Test
    public void withTypeNarrowsToTheTypesItNames() {
        Assert.assertEquals(List.of("DV_QUANTITY", "DV_COUNT"), RmTypes.preferred(CHOICE, "QUANTITY"));
        Assert.assertEquals(List.of("DV_TEXT"), RmTypes.preferred(CHOICE, "STRING"));
        Assert.assertEquals(List.of("DV_COUNT"), RmTypes.preferred(CHOICE, "DV_COUNT"));
        Assert.assertEquals(List.of("DV_CODED_TEXT"),
                            RmTypes.preferred(List.of("DV_CODED_TEXT", "DV_TEXT"), "CODING"));
    }

    @Test
    public void noneOrATypeTheTemplateDoesNotAllowPicksNothing() {
        Assert.assertEquals(CHOICE, RmTypes.preferred(CHOICE, null));
        Assert.assertEquals(CHOICE, RmTypes.preferred(CHOICE, "NONE"));
        Assert.assertEquals(CHOICE, RmTypes.preferred(CHOICE, "IDENTIFIER"));
    }
}
