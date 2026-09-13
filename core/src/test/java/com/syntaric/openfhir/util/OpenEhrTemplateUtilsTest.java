package com.syntaric.openfhir.util;

import com.syntaric.openfhir.db.entity.OptEntity;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;

/**
 * Covers issue #119: a referenced-but-missing operational template used to reach the SDK's OPTParser as a null,
 * which dereferenced it in its constructor and surfaced as an opaque NullPointerException.
 */
public class OpenEhrTemplateUtilsTest {

    private final OpenEhrTemplateUtils templateUtils = new OpenEhrTemplateUtils();

    @Test
    public void missingTemplateIsReportedAsTemplateNotFound() {
        final TemplateNotFoundException thrown = Assert.assertThrows(TemplateNotFoundException.class,
                () -> templateUtils.parseWebTemplate(null, "Growth chart1"));

        Assert.assertEquals("Growth chart1", thrown.getTemplateId());
        Assert.assertTrue("message should name the missing template, was: " + thrown.getMessage(),
                thrown.getMessage().contains("Growth chart1"));
    }

    @Test
    public void unparseableTemplateIsReportedAsInvalidTemplate() {
        final OptEntity entity = new OptEntity();
        entity.setTemplateId("broken");
        entity.setContent("this is not an operational template");

        final InvalidTemplateException thrown = Assert.assertThrows(InvalidTemplateException.class,
                () -> templateUtils.parseWebTemplate(entity, "broken"));

        Assert.assertEquals("broken", thrown.getTemplateId());
    }

    @Test
    public void templateStoredWithoutContentIsReportedAsInvalidTemplate() {
        final OptEntity entity = new OptEntity();
        entity.setTemplateId("empty");
        entity.setContent(null);

        Assert.assertThrows(InvalidTemplateException.class,
                () -> templateUtils.parseWebTemplate(entity, "empty"));
    }

    @Test
    public void validTemplateStillParses() throws IOException {
        final OptEntity entity = new OptEntity();
        entity.setTemplateId("kds_laborbericht");
        entity.setContent(read("/kds/laborbericht/KDS_Laborbericht.opt"));

        Assert.assertNotNull(templateUtils.parseWebTemplate(entity, "KDS_Laborbericht"));
    }

    private String read(final String classpathResource) throws IOException {
        try (final InputStream in = getClass().getResourceAsStream(classpathResource)) {
            Assert.assertNotNull("fixture missing: " + classpathResource, in);
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        }
    }
}
