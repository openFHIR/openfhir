package com.syntaric.openfhir.mapping.helpers;

import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class KdsLaborberichtHelpersTest extends GenericHelpersTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT_MAPPING = "/kds/core/projects/org.highmed/KDS/laborbericht/KDS_laborbericht.context.yaml";
    final String OPT_LOCATION = "/kds/laborbericht/";
    final String OPT = "KDS_Laborbericht.opt";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(OPT_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
    }

    @Test
    public void assertKdsLaborberichtHelpers() {
        final String templateId = context.getContext().getTemplate().getId();
        final String start = context.getContext().getStart();

        final List<MappingHelper> allHelpers = helpersCreator.constructHelpers(templateId, start,
                                                                               context.getContext().getArchetypes(),
                                                                               webTemplate).values()
                .stream()
                .flatMap(List::stream)
                .toList();

        assertHelpersMatchJson(allHelpers, "KdsLaborberichtHelpersTest.json");
    }
}
