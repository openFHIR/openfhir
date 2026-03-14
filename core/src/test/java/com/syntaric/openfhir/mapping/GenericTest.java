package com.syntaric.openfhir.mapping;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPathEvaluationContext;
import ca.uhn.fhir.parser.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.syntaric.openfhir.TestOpenFhirMappingContext;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.mapping.custommappings.CustomMappingRegistry;
import com.syntaric.openfhir.mapping.helpers.AqlToFlatPathConverter;
import com.syntaric.openfhir.mapping.helpers.HelpersCreator;
import com.syntaric.openfhir.mapping.helpers.OpenEhrFlatPathDataExtractor;
import com.syntaric.openfhir.mapping.helpers.parser.*;
import com.syntaric.openfhir.mapping.toaql.ToAql;
import com.syntaric.openfhir.mapping.tofhir.ToFhir;
import com.syntaric.openfhir.mapping.tofhir.ToFhirInstantiator;
import com.syntaric.openfhir.mapping.tofhir.ToFhirMappingEngine;
import com.syntaric.openfhir.mapping.tofhir.ToFhirPrePostProcessor;
import com.syntaric.openfhir.mapping.toopenehr.ToOpenEhr;
import com.syntaric.openfhir.mapping.toopenehr.ToOpenEhrMappingEngine;
import com.syntaric.openfhir.mapping.toopenehr.ToOpenEhrNullFlavour;
import com.syntaric.openfhir.mapping.toopenehr.ToOpenEhrPrePostProcessor;
import com.syntaric.openfhir.terminology.NoOpTerminologyTranslator;
import com.syntaric.openfhir.util.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Before;
import org.mockito.MockitoAnnotations;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;

import java.io.IOException;
import java.io.InputStream;

public abstract class GenericTest {

    protected final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
    protected final OpenFhirMapperUtils openFhirMapperUtils = new OpenFhirMapperUtils();
    protected final FhirConnectModelMerger fhirConnectModelMerger = new FhirConnectModelMerger();
    protected final FhirPathR4 fhirPath = new FhirPathR4(FhirContext.forR4());
    protected final JsonParser jsonParser = (JsonParser) FhirContext.forR4().newJsonParser();

    protected StandardsAsserter standardsAsserter = new StandardsAsserter();
    protected TestOpenFhirMappingContext repo;
    protected ToFhir toFhir;
    protected ToOpenEhr toOpenEhr;
    protected ToAql toAql;
    protected FhirConnectContext context;
    protected OPERATIONALTEMPLATE operationaltemplate;
    protected String operationaltemplateSerialized;
    protected WebTemplate webTemplate;
    protected HelpersCreator helpersCreator;
    protected ToFhirMappingEngine toFhirMappingEngine;
    protected OpenEhrFlatPathDataExtractor openEhrFlatPathDataExtractor;
    protected FhirInstanceCreator fhirInstanceCreator;
    protected FhirInstancePopulator fhirInstancePopulator;

    AutoCloseable closeable;

    @Before
    public void init() {
        closeable = MockitoAnnotations.openMocks(this); // Initialize mocks

        repo = new TestOpenFhirMappingContext(fhirConnectModelMerger);
        fhirPath.setEvaluationContext(new IFhirPathEvaluationContext() {
            // todo!!
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                return ((Reference) theContext).getResource();
            }
        });

        customMocks();

        final FhirInstanceCreatorUtility fhirInstanceCreatorUtility = new FhirInstanceCreatorUtility(
                openFhirStringUtils);
        final FhirValueReaders readers = new FhirValueReaders(openFhirMapperUtils);
        final OpenFhirMapperUtils openFhirMapperUtils = new OpenFhirMapperUtils();
        final FhirContext ctx = FhirContext.forR4();
        helpersCreator = new HelpersCreator(repo, new AqlToFlatPathConverter(
                openFhirStringUtils,
                openFhirMapperUtils), openFhirStringUtils);
        openEhrFlatPathDataExtractor = new OpenEhrFlatPathDataExtractor(
                openFhirStringUtils,
                new ValueToFHIRParser(
                        new TemporalParser(
                                readers),
                        new QuantityParser(
                                readers),
                        new CodedParser(readers),
                        new MediaParser(readers),
                        new TextParser(readers),
                        new IdentifierParser(
                                readers)
                ));
        fhirInstanceCreator = new FhirInstanceCreator(openFhirStringUtils,
                                                                                fhirInstanceCreatorUtility);
        fhirInstancePopulator = new FhirInstancePopulator(
                new NoOpPrePostFhirInstancePopulator(), new NoOpTerminologyTranslator());
        toFhirMappingEngine = new ToFhirMappingEngine(
                new OpenEhrConditionEvaluator(openFhirStringUtils),
                fhirInstanceCreator,
                fhirInstanceCreatorUtility,
                new FhirPathR4(ctx),
                openEhrFlatPathDataExtractor,
                openFhirStringUtils,
                fhirInstancePopulator,
                new ToFhirInstantiator(fhirInstanceCreator),
                new CustomMappingRegistry(),
                openFhirMapperUtils);
        toFhir = new ToFhir(new FlatJsonMarshaller(),
                            new OpenEhrCachedUtils(null),
                            new Gson(),
                            helpersCreator,
                            new ToFhirPrePostProcessor(FhirContext.forR4Cached()),
                            toFhirMappingEngine);

        final OpenEhrPopulator openEhrPopulator = new OpenEhrPopulator(openFhirMapperUtils,
                                                                       new NoOpTerminologyTranslator(),
                                                                       new NoOpPrePostOpenEhrPopulator(),
                                                                       openFhirStringUtils);
        toOpenEhr = new ToOpenEhr(new ToOpenEhrPrePostProcessor(ctx),
                                  new FlatJsonUnmarshaller(),
                                  new OpenEhrCachedUtils(null),
                                  helpersCreator,
                                  new Gson(),
                                  new ToOpenEhrMappingEngine(fhirPath,
                                                             openFhirStringUtils,
                                                             openEhrPopulator,
                                                             openFhirMapperUtils,
                                                             new ToOpenEhrNullFlavour(openFhirStringUtils,
                                                                                      openEhrPopulator,
                                                                                      fhirPath),
                                                             new CustomMappingRegistry()),
                                  fhirPath,
                                  openFhirStringUtils);


        prepareState();
    }

    protected abstract void prepareState();

    protected void customMocks() {

    }


    protected Bundle getTestBundle(String path) {
        InputStream is = getClass().getResourceAsStream(path);
        return (Bundle) jsonParser.parseResource(is);
    }

    protected String getFlat(final String path) {
        final InputStream inputStream = this.getClass().getResourceAsStream(path);
        try {
            return IOUtils.toString(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected FhirConnectContext getContext(final String path) {
        final ObjectMapper yaml = OpenFhirTestUtility.getYaml();
        final InputStream inputStream = this.getClass().getResourceAsStream(path);
        try {
            return yaml.readValue(inputStream, FhirConnectContext.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected OPERATIONALTEMPLATE getOperationalTemplate() {
        try {
            return TemplateDocument.Factory.parse(operationaltemplateSerialized).getTemplate();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected String getFile(String path) {
        InputStream is = getClass().getResourceAsStream(path);
        try {
            return IOUtils.toString(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
