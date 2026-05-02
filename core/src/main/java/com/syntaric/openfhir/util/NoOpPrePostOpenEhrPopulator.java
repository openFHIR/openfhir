package com.syntaric.openfhir.util;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.terminology.OfCoding;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.Base;

public class NoOpPrePostOpenEhrPopulator implements PrePostOpenEhrPopulatorInterface {

    @Override
    public void prePopulateElement(final MappingHelper mappingHelper,
                                   final String openEhrPath, final IBase extractedValue, final String openEhrType,
                                   final JsonObject constructingFlat, final Terminology terminology,
                                   final List<OfCoding> availableCodings) {

    }

    @Override
    public void postPopulateElement(final MappingHelper mappingHelper,
                                    String openEhrPath,
                                    final IBase extractedValue,
                                    final String openEhrType,
                                    final JsonObject constructingFlat,
                                    final Terminology terminology,
                                    final List<OfCoding> availableCodings,
                                    final List<String> addedFlatPaths,
                                    final List<String> addedValues) {

    }
}
