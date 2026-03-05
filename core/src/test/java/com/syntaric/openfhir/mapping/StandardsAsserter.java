package com.syntaric.openfhir.mapping;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.gson.Gson;
import com.nedap.archie.rm.composition.Composition;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.hl7.fhir.r4.model.Bundle;
import org.json.JSONObject;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.skyscreamer.jsonassert.JSONAssert;

public class StandardsAsserter {

    private static final Gson GSON = new Gson();

    @SneakyThrows
    public void assertComposition(Composition composition, String expectedClasspathJson,
                                  OPERATIONALTEMPLATE operationalTemplate) {
        JSONObject actual = new JSONObject(new CanonicalJson().marshal(composition));
        JSONObject expected = loadJsonObject(expectedClasspathJson);
        OptCompositionValidator.assertValid(operationalTemplate, composition);
        JSONAssert.assertEquals(expected, actual, true);
    }

    @SneakyThrows
    public void assertCompositionWihtoutOPTValidataion(Composition composition, String expectedClasspathJson,
                                                       OPERATIONALTEMPLATE operationalTemplate) {
        JSONObject actual = new JSONObject(new CanonicalJson().marshal(composition));
        JSONObject expected = loadJsonObject(expectedClasspathJson);
        JSONAssert.assertEquals(expected, actual, true);
    }

//    public void assertBundle(Bundle bundle) { TODO add FHIR profile validation
//        // Create an NPM Package Support module and load one package in from
//        // the classpath
//        FhirContext ctx = FhirContext.forR4();
//        NpmPackageValidationSupport npmPackageSupport = new NpmPackageValidationSupport(ctx);
//        try {
//            npmPackageSupport.loadPackageFromClasspath("kds/packages/de.medizininformatikinitiative.kerndatensatz.diagnose-2025.0.1-snapshots.tgz");
//            npmPackageSupport.loadPackageFromClasspath("kds/packages/de.medizininformatikinitiative.kerndatensatz.base-2026.0.0.tgz");
//            npmPackageSupport.loadPackageFromClasspath("kds/packages/de.medizininformatikinitiative.kerndatensatz.fall-2025.0.1.tgz");
//            npmPackageSupport.loadPackageFromClasspath("kds/packages/de.medizininformatikinitiative.kerndatensatz.meta-2025.0.0.tgz");
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        // Create a support chain including the NPM Package Support
//        ValidationSupportChain validationSupportChain = new ValidationSupportChain(
//                npmPackageSupport,
//                new DefaultProfileValidationSupport(ctx),
//                new CommonCodeSystemsTerminologyService(ctx),
//                new SnapshotGeneratingValidationSupport(ctx));
//
//        // Create a validator. Note that for good performance you can create as many validator objects
//        // as you like, but you should reuse the same validation support object in all of the,.
//        FhirValidator validator = ctx.newValidator();
//        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupportChain);
//        validator.registerValidatorModule(instanceValidator);
//
//
//        // Perform the validation
//        ValidationResult outcome = validator.validateWithResult(bundle);
//        if (!outcome.isSuccessful()) {
//            throw new AssertionError("Bundle did not validate: " + outcome.getMessages().toString());
//        }else  {
//            System.out.println("Bundle validated successfully.");
//        }
//    }

    @SneakyThrows
    public void assertBundle(Bundle bundle, String expectedClasspathJson) {
        FhirContext ctx = FhirContext.forR4();
        IParser parser = ctx.newJsonParser();
        JSONObject actual = new JSONObject(parser.encodeResourceToString(bundle));
        JSONObject expected = loadJsonObject(expectedClasspathJson);
        JSONAssert.assertEquals(expected, actual, true);
    }

    private JSONObject loadJsonObject(String classpathLocation) {
        InputStream is = getClass().getResourceAsStream(classpathLocation);
        try {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return new JSONObject(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON file", e);
        }
    }

}
