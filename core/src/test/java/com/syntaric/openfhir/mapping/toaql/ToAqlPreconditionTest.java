package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.aql.FhirQueryParam;
import com.syntaric.openfhir.fc.OpenFhirFhirConfig;
import com.syntaric.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import com.syntaric.openfhir.fc.schema.model.Preprocessor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static com.syntaric.openfhir.fc.FhirConnectConst.CONDITION_OPERATOR_NOT_OF;
import static com.syntaric.openfhir.fc.FhirConnectConst.CONDITION_OPERATOR_ONE_OF;

public class ToAqlPreconditionTest {

    private ToAql toAql;

    @Before
    public void setUp() {
        toAql = new ToAql(null, null, null, null, new ToAqlMappingEngine(new OpenEhrAqlPopulator()), null, null, null);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // No preprocessor / no conditions — should always pass
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void preconditionPasses_noPreprocessor_returnsTrue() {
        final OpenFhirFhirConnectModelMapper mapper = mapperWithPreprocessor(null);
        Assert.assertTrue(toAql.preconditionPasses("Observation", mapper, List.of(new FhirQueryParam("code", "123"))));
    }

    @Test
    public void preconditionPasses_preprocessorNullConditions_returnsTrue() {
        final Preprocessor preprocessor = new Preprocessor();
        // fhirConditions left null
        final OpenFhirFhirConnectModelMapper mapper = mapperWithPreprocessor(preprocessor);
        Assert.assertTrue(toAql.preconditionPasses("Observation", mapper, List.of(new FhirQueryParam("code", "123"))));
    }

    @Test
    public void preconditionPasses_preprocessorEmptyConditions_returnsTrue() {
        final Preprocessor preprocessor = new Preprocessor();
        preprocessor.setFhirConditions(List.of());
        final OpenFhirFhirConnectModelMapper mapper = mapperWithPreprocessor(preprocessor);
        Assert.assertTrue(toAql.preconditionPasses("Observation", mapper, List.of(new FhirQueryParam("code", "123"))));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // "one of" operator
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void preconditionPasses_oneOf_valueInCriterias_returnsTrue() {
        // "code" resolves to "Observation.code"; condition targetRoot starts with that path
        final OpenFhirFhirConnectModelMapper mapper = mapperWithCondition(
                "Observation", "code", CONDITION_OPERATOR_ONE_OF, List.of("29463-7", "55284-4"));

        Assert.assertTrue(toAql.preconditionPasses("Observation", mapper, List.of(new FhirQueryParam("code", "29463-7"))));
    }

    @Test
    public void preconditionPasses_oneOf_valueNotInCriterias_returnsFalse() {
        final OpenFhirFhirConnectModelMapper mapper = mapperWithCondition(
                "Observation", "code", CONDITION_OPERATOR_ONE_OF, List.of("29463-7", "55284-4"));

        Assert.assertFalse(toAql.preconditionPasses("Observation", mapper, List.of(new FhirQueryParam("code", "99999-9"))));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // "not of" operator
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void preconditionPasses_notOf_valueInCriterias_returnsFalse() {
        final OpenFhirFhirConnectModelMapper mapper = mapperWithCondition(
                "Observation", "code", CONDITION_OPERATOR_NOT_OF, List.of("29463-7"));

        Assert.assertFalse(toAql.preconditionPasses("Observation", mapper, List.of(new FhirQueryParam("code", "29463-7"))));
    }

    @Test
    public void preconditionPasses_notOf_valueNotInCriterias_returnsTrue() {
        final OpenFhirFhirConnectModelMapper mapper = mapperWithCondition(
                "Observation", "code", CONDITION_OPERATOR_NOT_OF, List.of("29463-7"));

        Assert.assertTrue(toAql.preconditionPasses("Observation", mapper, List.of(new FhirQueryParam("code", "99999-9"))));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Condition path does not match query param path — condition is skipped, returns true
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void preconditionPasses_conditionPathDoesNotMatchQueryParam_returnsTrue() {
        // condition is on "Observation.status", but query param is "code" (path "Observation.code")
        final OpenFhirFhirConnectModelMapper mapper = mapperWithCondition(
                "Observation", "status", CONDITION_OPERATOR_ONE_OF, List.of("final"));

        // assert false because the mapping says it's only relevant for those where observation.status is final
        Assert.assertFalse(toAql.preconditionPasses("Observation", mapper, List.of(new FhirQueryParam("code", "29463-7"))));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------------------------------

    private OpenFhirFhirConnectModelMapper mapperWithPreprocessor(final Preprocessor preprocessor) {
        final FhirConnectModel model = new FhirConnectModel();
        model.setPreprocessor(preprocessor);
        return OpenFhirFhirConnectModelMapper.builder()
                .originalModel(model)
                .fhirConfig(new OpenFhirFhirConfig().withResource("Observation"))
                .build();
    }

    private OpenFhirFhirConnectModelMapper mapperWithCondition(final String targetRoot,
                                                               final String targetAttribute,
                                                               final String operator,
                                                               final List<String> criterias) {
        final Condition condition = new Condition();
        condition.setTargetRoot(targetRoot);
        condition.setTargetAttribute(targetAttribute);
        condition.setOperator(operator);
        condition.setCriterias(criterias);

        final Preprocessor preprocessor = new Preprocessor();
        preprocessor.setFhirConditions(List.of(condition));

        return mapperWithPreprocessor(preprocessor);
    }
}
