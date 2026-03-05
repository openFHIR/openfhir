package com.syntaric.openfhir.mapping.helpers;

import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class KdsTodesursacheHelpersTest extends GenericHelpersTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT_MAPPING = "/kds/core/projects/org.highmed/KDS/todesursache/KDS_Todesursache.context.yaml";
    final String OPT_LOCATION = "/kds/todesursache/";
    final String OPT = "KDS_Todesursache.opt";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(OPT_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
    }

    @Test
    public void assertKdsTodesursacheHelpers() {
        final String templateId = context.getContext().getTemplate().getId();
        final String start = context.getContext().getStart();

        final List<MappingHelper> allHelpers = helpersCreator.constructHelpers(templateId, start,
                                                                               context.getContext().getArchetypes(),
                                                                               webTemplate).values()
                .stream()
                .flatMap(List::stream)
                .toList();

        assertHelpersMatchJson(allHelpers, "KdsTodesursacheHelpersTest.json");
    }
}
