package com.syntaric.openfhir.mapping.helpers.parser;


import static com.syntaric.openfhir.fc.FhirConnectConst.CODE_PHRASE;
import static com.syntaric.openfhir.fc.FhirConnectConst.DV_BOOL;
import static com.syntaric.openfhir.fc.FhirConnectConst.DV_CODED_TEXT;
import static com.syntaric.openfhir.fc.FhirConnectConst.DV_COUNT;
import static com.syntaric.openfhir.fc.FhirConnectConst.DV_DATE;
import static com.syntaric.openfhir.fc.FhirConnectConst.DV_DATE_TIME;
import static com.syntaric.openfhir.fc.FhirConnectConst.DV_IDENTIFIER;
import static com.syntaric.openfhir.fc.FhirConnectConst.DV_INTERVAL;
import static com.syntaric.openfhir.fc.FhirConnectConst.DV_MULTIMEDIA;
import static com.syntaric.openfhir.fc.FhirConnectConst.DV_ORDINAL;
import static com.syntaric.openfhir.fc.FhirConnectConst.DV_PROPORTION;
import static com.syntaric.openfhir.fc.FhirConnectConst.DV_QUANTITY;
import static com.syntaric.openfhir.fc.FhirConnectConst.DV_TEXT;
import static com.syntaric.openfhir.fc.FhirConnectConst.DV_TIME;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.jsonwebtoken.lang.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ValueToFHIRParser {

    private final TemporalParser temporalParser;
    private final QuantityParser quantityParser;
    private final CodedParser codedParser;
    private final MediaParser mediaParser;
    private final TextParser textParser;
    private final IdentifierParser identifierParser;

    @Autowired
    public ValueToFHIRParser(final TemporalParser temporalParser,
                             final QuantityParser quantityParser,
                             final CodedParser codedParser,
                             final MediaParser mediaParser,
                             final TextParser textParser,
                             final IdentifierParser identifierParser) {
        this.temporalParser = temporalParser;
        this.quantityParser = quantityParser;
        this.codedParser = codedParser;
        this.mediaParser = mediaParser;
        this.textParser = textParser;
        this.identifierParser = identifierParser;
    }

    public DataWithIndex parse(final List<String> joinedValues,
                               final List<String> types,
                               final JsonObject valueHolder,
                               final String path,
                               final int lastIndex,
                               final String fhirPath) {

        if (types == null || types.isEmpty()) {
            return textParser.string(valueHolder, lastIndex, path);
        }

        //loop over all possible types and check which yields any result
        for (String targetType : types.stream().filter(Objects::nonNull).distinct().toList()) {
            DataWithIndex result = switch (targetType) {
                case "INTERVAL_EVENT" -> temporalParser.eventInterval(joinedValues, valueHolder, lastIndex, path);
                case "POINT_EVENT" -> temporalParser.eventPoint(joinedValues, valueHolder, lastIndex, path);
                case "EVENT" -> temporalParser.eventByWidth(joinedValues, valueHolder, lastIndex, path);
                case DV_DATE_TIME, "DATETIME" -> temporalParser.dateTime(valueHolder, lastIndex, path);
                case DV_TIME, "TIME" -> temporalParser.time(valueHolder, lastIndex, path);
                case DV_BOOL, "BOOL" -> temporalParser.bool(valueHolder, lastIndex, path);
                case DV_DATE, "DATE" -> temporalParser.date(valueHolder, lastIndex, path);
                case DV_INTERVAL -> {
                    boolean wantsRange = fhirPath != null && fhirPath.contains("as(Range)");
                    yield wantsRange
                            ? temporalParser.range(joinedValues, valueHolder, lastIndex, path)
                            : temporalParser.interval(joinedValues, valueHolder, lastIndex, path);
                }
                case DV_PROPORTION, "PROPORTION" ->
                        quantityParser.proportion(joinedValues, valueHolder, lastIndex, path);
                case DV_QUANTITY, "QUANTITY" -> quantityParser.quantity(joinedValues, valueHolder, lastIndex, path);
                case DV_COUNT -> quantityParser.count(valueHolder, lastIndex, path);

                case DV_CODED_TEXT, DV_ORDINAL, "CODEABLECONCEPT" ->
                        codedParser.codeableConcept(joinedValues, valueHolder, lastIndex, path);

                case CODE_PHRASE, "CODING" -> codedParser.coding(joinedValues, valueHolder, lastIndex, path);

                case DV_MULTIMEDIA, "MEDIA" -> mediaParser.attachment(valueHolder, lastIndex, path);

                case DV_TEXT, "STRING", "TEXT" ->
                        textParser.string(valueHolder, lastIndex, path) == null ? codedParser.codeableConcept(joinedValues, valueHolder, lastIndex, path) : textParser.string(valueHolder, lastIndex, path);

                case DV_IDENTIFIER, "IDENTIFIER" ->
                        identifierParser.identifier(joinedValues, valueHolder, lastIndex, path);

                default -> textParser.string(valueHolder, lastIndex, path);
            };
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
