package com.syntaric.openfhir.producers;

import ca.uhn.fhir.fhirpath.IFhirPath;
import com.syntaric.openfhir.fc.schema.Spec;
import java.util.List;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/**
 * Every spelling of a cast the openEHR -> FHIR direction accepts must select the same element when the FHIR ->
 * openEHR direction evaluates it with HAPI's FHIRPath engine. HAPI on its own rejects a HAPI class name
 * ({@code as(StringType)}) and a lowercase complex type, matches {@code ofType()} case-sensitively and selects
 * nothing for a System type.
 */
public class CastNormalizingFhirPathTest {

    private final FhirContextRegistry registry = new FhirContextRegistry();

    @Test
    public void everySpellingOfAStringCastSelectsTheString() {
        final Extension extension = new Extension("http://example.org", new StringType("text"));
        for (final String cast : List.of("string", "String", "STRING", "FHIR.string", "StringType",
                                         "System.String")) {
            assertSelects(extension, "value.as(" + cast + ")", StringType.class);
            assertSelects(extension, "value.ofType(" + cast + ")", StringType.class);
        }
        assertSelectsNothing(extension, "value.as(dateTime)");
    }

    @Test
    public void everySpellingOfADateTimeCastSelectsTheDateTime() {
        final Extension extension = new Extension("http://example.org", new DateTimeType("2022-02-03T04:05:06"));
        for (final String cast : List.of("dateTime", "DateTime", "datetime", "FHIR.dateTime", "DateTimeType",
                                         "System.DateTime")) {
            assertSelects(extension, "value.as(" + cast + ")", DateTimeType.class);
            assertSelects(extension, "value.ofType(" + cast + ")", DateTimeType.class);
        }
        assertSelectsNothing(extension, "value.as(string)");
    }

    @Test
    public void everySpellingOfABooleanCastSelectsTheBoolean() {
        final Extension extension = new Extension("http://example.org", new BooleanType(true));
        for (final String cast : List.of("boolean", "Boolean", "FHIR.boolean", "BooleanType", "System.Boolean")) {
            assertSelects(extension, "value.as(" + cast + ")", BooleanType.class);
        }
        assertSelectsNothing(extension, "value.as(dateTime)");
    }

    @Test
    public void everySpellingOfAComplexCastSelectsTheElement() {
        final Extension extension = new Extension("http://example.org", new Quantity(3));
        for (final String cast : List.of("Quantity", "quantity", "FHIR.Quantity")) {
            assertSelects(extension, "value.as(" + cast + ")", Quantity.class);
        }
        assertSelectsNothing(extension, "value.as(string)");
    }

    @Test
    public void stu3_lowercaseAndLegacySpellingSelectTheString() {
        final IFhirPath fhirPath = registry.getFhirPath(Spec.Version.STU3);
        final org.hl7.fhir.dstu3.model.Extension extension = new org.hl7.fhir.dstu3.model.Extension(
                "http://example.org", new org.hl7.fhir.dstu3.model.StringType("text"));
        for (final String cast : List.of("string", "String", "FHIR.string", "StringType")) {
            final List<IBase> result = fhirPath.evaluate(extension, "value.as(" + cast + ")", IBase.class);
            Assert.assertEquals(cast, 1, result.size());
        }
    }

    private void assertSelects(final Extension extension, final String path, final Class<? extends Type> type) {
        final List<IBase> result = registry.getFhirPath(Spec.Version.R4).evaluate(extension, path, IBase.class);
        Assert.assertEquals(path, 1, result.size());
        Assert.assertTrue(path, type.isInstance(result.get(0)));
        Assert.assertSame(path, extension.getValue(), result.get(0));
    }

    private void assertSelectsNothing(final Extension extension, final String path) {
        Assert.assertTrue(path, registry.getFhirPath(Spec.Version.R4).evaluate(extension, path, IBase.class)
                .isEmpty());
    }
}
