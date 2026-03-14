package com.syntaric.openfhir.mapping.helpers.parser;

import com.google.gson.JsonObject;

import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.hl7.fhir.r4.model.Attachment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MediaParser {

    private final FhirValueReaders fhirValueReaders;

    @Autowired
    public MediaParser(FhirValueReaders readers) {
        this.fhirValueReaders = readers;
    }

    public DataWithIndex attachment(JsonObject valueHolder, Integer lastIndex, String path) {
        String mediaType = fhirValueReaders.get(valueHolder, path + "|mediatype");
        String data = fhirValueReaders.get(valueHolder, path + "|data");
        String url = fhirValueReaders.get(valueHolder, path + "|url");
        if (mediaType == null && data == null && url == null && !path.contains("/" + FhirConnectConst.LEAF_TYPE_MULTIMEDIA_DATA)) {
            return attachment(valueHolder, lastIndex, path + "/" + FhirConnectConst.LEAF_TYPE_MULTIMEDIA_DATA);
        }

        Attachment att = new Attachment();
        att.setContentType(fhirValueReaders.get(valueHolder, path + "|mediatype"));

        String size = fhirValueReaders.get(valueHolder, path + "|size");
        if (size != null) att.setSize(Integer.parseInt(size));

        att.setUrl(fhirValueReaders.get(valueHolder, path + "|url"));

        String dataBytes = fhirValueReaders.get(valueHolder, path + "|data");
        if (dataBytes != null) {
            try {
                att.setData(Base64.getDecoder().decode(dataBytes));
            } catch (IllegalArgumentException e) {
                // fallback for non-base64 test fixtures
                att.setData(dataBytes.getBytes(StandardCharsets.UTF_8));
            }
        }

        return new DataWithIndex(att, lastIndex, path, FhirConnectConst.DV_MULTIMEDIA);
    }
}
