package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * The helpers a query is matched against are the topmost helpers of each relevant model: their children are
 * reached through them (and through their fhirConditions), so a child is matched once and only where its parent
 * lets it.
 */
public class ToAqlRelevantHelpersTest {

    private final ToAql toAql = new ToAql(null, null, null, null, null, null, null, null);

    private static MappingHelper helper(final String model, final String name, final MappingHelper... children) {
        final MappingHelper helper = new MappingHelper();
        helper.setModelMetadataName(model);
        helper.setMappingName(name);
        helper.getChildren().addAll(List.of(children));
        return helper;
    }

    private static OpenFhirFhirConnectModelMapper model(final String name) {
        final OpenFhirFhirConnectModelMapper mapper = new OpenFhirFhirConnectModelMapper();
        mapper.setName(name);
        return mapper;
    }

    private List<String> relevant(final List<MappingHelper> tree, final String... models) {
        return toAql.getRelevantMappingHelpers(List.of(models).stream().map(ToAqlRelevantHelpersTest::model).toList(),
                                               tree, new ArrayList<>())
                .stream().map(MappingHelper::getMappingName).toList();
    }

    @Test
    public void aRelevantHelpersChildrenAreReachedThroughIt() {
        final MappingHelper status = helper("problem", "status",
                                            helper("problem", "status.confirmed"), helper("problem", "status.suspected"));
        final List<MappingHelper> tree = List.of(helper("problem", "code"), status);

        Assert.assertEquals(List.of("code", "status"), relevant(tree, "problem"));
    }

    @Test
    public void aModelSlottedBelowAnotherIsFoundWhereItSits() {
        final MappingHelper section = helper("section", "entries",
                                             helper("problem", "code", helper("problem", "code.text")),
                                             helper("allergy", "substance"));
        final List<MappingHelper> tree = List.of(helper("section", "title"), section);

        Assert.assertEquals(List.of("code"), relevant(tree, "problem"));
        Assert.assertEquals(List.of("code", "substance"), relevant(tree, "problem", "allergy"));
        // a relevant model's slotted models are reached through it as well
        Assert.assertEquals(List.of("title", "entries"), relevant(tree, "section", "problem"));
    }
}
