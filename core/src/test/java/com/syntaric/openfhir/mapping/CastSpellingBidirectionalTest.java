package com.syntaric.openfhir.mapping;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.tofhir.ToFhirInstantiator;
import com.syntaric.openfhir.operations.MappingIssueCollector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;

/**
 * A FHIRPath cast can be spelled in several ways: the FHIR spelling ({@code as(string)}, {@code as(dateTime)}), the
 * capitalised one tools used to emit ({@code as(String)}), namespaced ({@code as(FHIR.string)}) and the legacy HAPI
 * class name ({@code as(StringType)}). Every one of them must build the same FHIR from openEHR and read the same
 * value back from FHIR. Before, a lowercase primitive cast built nothing in the openEHR -> FHIR direction, without an
 * error, and a HAPI class name broke the mapping in the FHIR -> openEHR direction.
 * <p>
 * The model mapper is a template (/cast_spelling/medication-order.model.yml.template) mapping a DV_TEXT, a
 * DV_DATE_TIME and a DV_BOOLEAN of the medication order template to the {@code value} of three MedicationRequest
 * extensions; each test writes it out with the cast spelling under test.
 */
public class CastSpellingBidirectionalTest extends GenericTest {

    private static final String FIXTURES = "/cast_spelling/";
    private static final String OPT = "/medication_order/medication order.opt";
    private static final String FLAT = "/medication_order/medication_order_flat.json";

    private static final String COMMENT_URL = "http://example.org/comment";
    private static final String ORDER_START_URL = "http://example.org/order-start";
    private static final String SAFETY_OVERRIDE_URL = "http://example.org/safety-override";

    private static final String ORDER_0 = "medication_order/medication_order/order:0/";
    private static final List<String> MAPPED_FLAT_PATHS = List.of(
            "medication_order/medication_order/order:0/comment",
            "medication_order/medication_order/order:0/order_details/order_start_date_time",
            "medication_order/medication_order/order:0/medication_safety/exceptional_safety_override");

    private static final List<String> CAPITALISED = List.of("String", "DateTime", "Boolean");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String modelsDir;

    @Override
    protected void prepareState() {
        context = getContext(FIXTURES + "medication-order.context.yml");
        operationaltemplateSerialized = getFile(OPT);
        operationaltemplate = getOperationalTemplate();
        webTemplate = new OPTParser(operationaltemplate).parse();
        if (modelsDir != null) {
            repo.initRepository(context, operationaltemplate, modelsDir);
        }
    }

    @Test
    public void capitalisedCasts_mapAllThreeValues() {
        useCasts(CAPITALISED);

        final List<MedicationRequest> requests = toFhir();
        final Map<String, String> extensions = extensionValues(requests.get(0));
        Assert.assertEquals("string:Lorem ipsum", extensions.get(COMMENT_URL));
        Assert.assertEquals("dateTime:2022-02-03T04:05:06", extensions.get(ORDER_START_URL));
        Assert.assertEquals("boolean:true", extensions.get(SAFETY_OVERRIDE_URL));

        final Map<String, String> flat = mappedFlatValues(toOpenEhr(requests));
        Assert.assertEquals(flat.toString(), MAPPED_FLAT_PATHS.size(), flat.size());
        Assert.assertEquals("Lorem ipsum", flat.get(MAPPED_FLAT_PATHS.get(0)));
        Assert.assertEquals("true", flat.get(MAPPED_FLAT_PATHS.get(2)));
    }

    @Test
    public void lowercaseCasts_mapLikeCapitalised() {
        assertMapsLikeCapitalised(List.of("string", "dateTime", "boolean"));
    }

    @Test
    public void fhirNamespacedCasts_mapLikeCapitalised() {
        assertMapsLikeCapitalised(List.of("FHIR.string", "FHIR.dateTime", "FHIR.boolean"));
    }

    @Test
    public void legacyHapiClassCasts_mapLikeCapitalised() {
        assertMapsLikeCapitalised(List.of("StringType", "DateTimeType", "BooleanType"));
    }

    @Test
    public void anyCapitalisationOfAPrimitive_mapsLikeCapitalised() {
        assertMapsLikeCapitalised(List.of("STRING", "datetime", "bOOLEAN"));
    }

    /**
     * A cast whose type is not a FHIR type at all is reported as a warning naming the mapping and the path, and
     * nothing is built for it; the mappings next to it are unaffected and mapping does not fail.
     */
    @Test
    public void unknownCastType_warnsAndBuildsNothing() {
        useCasts(List.of("foo", "dateTime", "boolean"));
        final ListAppender<ILoggingEvent> logs = captureLogs(ToFhirInstantiator.class);

        final Map<String, String> extensions = extensionValues(toFhir().get(0));

        Assert.assertNull("nothing may be built for the unknown cast", extensions.get(COMMENT_URL));
        Assert.assertEquals("dateTime:2022-02-03T04:05:06", extensions.get(ORDER_START_URL));
        Assert.assertEquals("boolean:true", extensions.get(SAFETY_OVERRIDE_URL));

        final List<String> warnings = logs.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("unknown cast type 'foo'"))
                .collect(Collectors.toList());
        Assert.assertFalse("expected a warning about the unknown cast", warnings.isEmpty());
        Assert.assertTrue(warnings.get(0), warnings.get(0).contains("'commentValue'"));
        Assert.assertTrue(warnings.get(0), warnings.get(0).contains("value.as(foo)"));
    }

    /**
     * Maps the composition to FHIR and the FHIR back to openEHR with the given cast spellings (round trip), and
     * compares both results to those of the capitalised spelling.
     */
    private void assertMapsLikeCapitalised(final List<String> casts) {
        useCasts(CAPITALISED);
        final List<MedicationRequest> expectedRequests = toFhir();
        final Map<String, String> expectedFlat = mappedFlatValues(toOpenEhr(expectedRequests));

        useCasts(casts);
        final List<MedicationRequest> requests = toFhir();
        Assert.assertEquals(expectedRequests.size(), requests.size());
        for (int i = 0; i < requests.size(); i++) {
            Assert.assertEquals("openEHR -> FHIR with " + casts, extensionValues(expectedRequests.get(i)),
                                extensionValues(requests.get(i)));
        }
        Assert.assertEquals("FHIR -> openEHR with " + casts, expectedFlat, mappedFlatValues(toOpenEhr(requests)));
        Assert.assertEquals("FHIR (capitalised) -> openEHR with " + casts, expectedFlat,
                            mappedFlatValues(toOpenEhr(expectedRequests)));
    }

    /**
     * (Re)initialises the engine with the model mapper template, its ${string}, ${dateTime} and ${boolean} casts
     * replaced with the given ones.
     */
    private void useCasts(final List<String> casts) {
        try {
            final String model = getFile(FIXTURES + "medication-order.model.yml.template")
                    .replace("${string}", casts.get(0))
                    .replace("${dateTime}", casts.get(1))
                    .replace("${boolean}", casts.get(2));
            final Path dir = temporaryFolder.newFolder().toPath();
            Files.writeString(dir.resolve("medication-order.model.yml"), model, StandardCharsets.UTF_8);
            modelsDir = dir.toString();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        init();
    }

    private List<MedicationRequest> toFhir() {
        final JsonObject flat = new Gson().fromJson(getFlat(FLAT), JsonObject.class);
        // the fixture only has an order start date/time on the second order
        flat.addProperty(ORDER_0 + "order_details/order_start_date_time", "2022-02-03T04:05:06");
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(new Gson().toJson(flat), webTemplate);
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(composition), webTemplate);
        final List<MedicationRequest> requests = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(MedicationRequest.class::isInstance)
                .map(MedicationRequest.class::cast)
                .collect(Collectors.toList());
        Assert.assertFalse("expected a MedicationRequest per order", requests.isEmpty());
        return requests;
    }

    private JsonObject toOpenEhr(final List<MedicationRequest> requests) {
        final Bundle bundle = new Bundle();
        requests.forEach(request -> bundle.addEntry().setResource(request));
        final MappingIssueCollector issues = new MappingIssueCollector();
        final JsonObject flat = toOpenEhr.fhirToFlatJsonObject(context, bundle, webTemplate, issues);
        Assert.assertTrue("no mapping may be skipped: " + issues.getIssues(), issues.getIssues().stream()
                .noneMatch(issue -> issue.diagnostics().contains("Skipped")));
        return flat;
    }

    /**
     * The extensions of the request as url -> "fhirType:value".
     */
    private static Map<String, String> extensionValues(final MedicationRequest request) {
        final Map<String, String> values = new TreeMap<>();
        for (final Extension extension : request.getExtension()) {
            if (extension.getValue() != null) {
                values.put(extension.getUrl(),
                           extension.getValue().fhirType() + ":" + extension.getValue().primitiveValue());
            }
        }
        return values;
    }

    /**
     * The flat entries of the three mapped elements (and their pipe suffixes, if any).
     */
    private static Map<String, String> mappedFlatValues(final JsonObject flat) {
        final Map<String, String> values = new TreeMap<>();
        flat.entrySet().stream()
                .filter(entry -> MAPPED_FLAT_PATHS.stream().anyMatch(path -> entry.getKey().startsWith(path)))
                .forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsString()));
        return values;
    }

    private ListAppender<ILoggingEvent> captureLogs(final Class<?> loggerClass) {
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(loggerClass)).addAppender(appender);
        return appender;
    }
}
