package com.syntaric.openfhir.util;

import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.terminology.TerminologyTranslatorInterface;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseDatatype;
import org.hl7.fhir.instance.model.api.IBaseEnumeration;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TimeType;
import org.hl7.fhir.r4.model.Timing;
import org.hl7.fhir.r4.model.Timing.TimingRepeatComponent;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Used for populating a FHIR Base.
 *
 * <p>{@code data} is always an R4 {@link Base} (the internal representation produced by the
 * openEHR extraction pipeline). {@code toPopulate} may be any FHIR version's type
 * (STU3 / R4 / R4B / R5). Each {@code populate*} method first handles the R4 case
 * (same-version copy) and then falls through to version-aware helpers that apply the
 * extracted scalar values to the target object regardless of its FHIR version.
 */
@Slf4j
@Component
public class FhirInstancePopulator {

    private final PrePostFhirInstancePopulatorInterface prePostFhirInstancePopulatorInterface;
    private final TerminologyTranslatorInterface terminologyTranslator;

    @Autowired
    public FhirInstancePopulator(final PrePostFhirInstancePopulatorInterface prePostFhirInstancePopulatorInterface,
                                 final TerminologyTranslatorInterface terminologyTranslator) {
        this.prePostFhirInstancePopulatorInterface = prePostFhirInstancePopulatorInterface;
        this.terminologyTranslator = terminologyTranslator;
    }

    /**
     * data can be anything from OpenEhrToFhir.valueToDataPoint, a limited set of things.
     * <p>
     * Populates an element with the data. Population logic depends on the type of toPopulate object
     */
    public void populateElement(final MappingHelper mappingHelper,
                                final Object toPopulate,
                                final DataWithIndex dataH,
                                final String modelName,
                                final String mappingName,
                                final String fromPath,
                                final String toPath,
                                final Terminology terminology) {
        populateElement(mappingHelper, toPopulate, dataH.getData(), modelName, mappingName, fromPath, toPath, dataH.getIndex(),
                terminology);
    }

    public void populateElement(final MappingHelper mappingHelper,
                                final Object toPopulate,
                                final Base data,
                                final String modelName,
                                final String mappingName,
                                final String fromPath,
                                final String toPath,
                                final int index,
                                final Terminology terminology) {
        prePostFhirInstancePopulatorInterface.prePopulateElement(mappingHelper, toPopulate, data, modelName, mappingName, fromPath, toPath, index, terminology);

        if (toPopulate instanceof Extension && data instanceof IBaseDatatype) {
            setExtensionValue((Extension) toPopulate, (IBaseDatatype) data);
            return;
        }

        if (toPopulate instanceof List) {
            populateListElement(mappingHelper, (List<?>) toPopulate, data, modelName, mappingName, fromPath, toPath, index,
                    terminology);
            return;
        }

        handleSpecificTypePopulation(toPopulate, data, terminology);

        prePostFhirInstancePopulatorInterface.postPopulateElement(mappingHelper, toPopulate, data, modelName, mappingName, fromPath, toPath, index, terminology);
    }

    private Boolean objectIsEmpty(final Object lastElement) {
        try {
            return (Boolean) lastElement.getClass().getMethod("isEmpty").invoke(lastElement);
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            return false;
        }
    }

    private void setExtensionValue(final Extension extension, final IBaseDatatype data) {
        extension.setValue(data);
    }

    private void populateListElement(final MappingHelper mappingHelper,
                                     final List<?> toPopulate, final Base data, final String modelName,
                                     final String mappingName,
                                     final String fromPath, final String toPath, final int index,
                                     final Terminology terminology) {
        final Object lastElement = toPopulate.get(toPopulate.size() - 1);
        if (lastElement.toString() == null || objectIsEmpty(lastElement)) {
            populateElement(mappingHelper, lastElement, data, modelName, mappingName, fromPath, toPath,
                    index,
                    terminology); // Populate last element if empty
        } else {
            ((List<Object>) toPopulate).add(data); // Otherwise, add new entry
        }
    }

    protected void handleSpecificTypePopulation(final Object toPopulate, final Base data, final Terminology terminology) {
        if (data instanceof Quantity) {
            populateQuantity(toPopulate, (Quantity) data, terminology);
        } else if (data instanceof IntegerType) {
            populateIntegerType(toPopulate, (IntegerType) data, terminology);
        } else if (data instanceof DateTimeType) {
            populateDateTime(toPopulate, (DateTimeType) data);
        } else if (data instanceof TimeType) {
            populateTimeType(toPopulate, (TimeType) data);
        } else if (data instanceof Timing) {
            populateTiming(toPopulate, (Timing) data, terminology);
        } else if (data instanceof Identifier) {
            populateIdentifier(toPopulate, (Identifier) data, terminology);
        } else if (data instanceof DateType) {
            populateDateType(toPopulate, (DateType) data);
        } else if (data instanceof CodeableConcept) {
            populateCodeableConcept(toPopulate, (CodeableConcept) data, terminology);
        } else if (data instanceof Coding) {
            populateCoding(toPopulate, (Coding) data, terminology);
        } else if (data instanceof Attachment) {
            populateAttachment(toPopulate, (Attachment) data);
        } else if (data instanceof StringType) {
            populateStringType(toPopulate, (StringType) data, terminology);
        } else if (data instanceof BooleanType) {
            populateBooleanType(toPopulate, (BooleanType) data);
        } else if (data instanceof Period) {
            populatePeriodType(toPopulate, (Period) data);
        }
    }

    private void populateTiming(Object toPopulate, Timing data, Terminology terminology) {
        if (data.hasCode()) {
            data.getCode().getCoding().replaceAll(coding -> {
                final Coding translated = terminologyTranslator.translateToFhir(coding.getCode(), coding.getSystem(), null, terminology);
                return translated != null ? translated : coding;
            });
        }
        if (toPopulate instanceof Timing timingToPopulate) {
            data.copyValues(timingToPopulate);
        } else if (toPopulate instanceof TimingRepeatComponent timingRepeatComponent) {
            final TimingRepeatComponent repeat = data.getRepeat();
            repeat.copyValues(timingRepeatComponent);
        } else {
            populateTimingCrossVersion(toPopulate, data, terminology);
        }
    }

    private void populateQuantity(Object toPopulate, Quantity data, Terminology terminology) {
        final Coding translated = terminologyTranslator.translateToFhir(data.getCode(), data.getSystem(), null, terminology);
        if (translated != null) {
            if (StringUtils.isNotBlank(translated.getCode())) {
                data.setCode(translated.getCode());
            }
            if (StringUtils.isNotBlank(translated.getSystem())) {
                data.setSystem(translated.getSystem());
            }
            if (StringUtils.isNotBlank(translated.getDisplay())) {
                data.setUnit(translated.getDisplay());
            }
        }
        if (toPopulate instanceof Quantity) {
            data.copyValues((Quantity) toPopulate);
        } else if (toPopulate instanceof Ratio ratioToPopulate) {
            ratioToPopulate.setNumerator(data);
        } else if (toPopulate instanceof IntegerType integerTypeToPopulate) {
            integerTypeToPopulate.setValue(data.getValue().intValue());
        } else {
            populateQuantityCrossVersion(toPopulate, data.getValue(), data.getCode(), data.getSystem(), data.getUnit());
        }
    }

    private void populateDateTime(Object toPopulate, DateTimeType data) {
        if (toPopulate instanceof DateTimeType) {
            ((DateTimeType) toPopulate).setValue(data.getValue());
        } else if (toPopulate instanceof InstantType) {
            ((InstantType) toPopulate).setValue(data.getValue());
        } else {
            populateDateTimeCrossVersion(toPopulate, data.getValue(), data.getValueAsString());
        }
    }

    private void populateIntegerType(Object toPopulate, IntegerType data, Terminology terminology) {
        final Coding translated = terminologyTranslator.translateToFhir(String.valueOf(data.getValue()), null, null, terminology);
        if (translated != null && StringUtils.isNotBlank(translated.getCode())) {
            data.setValue(Integer.valueOf(translated.getCode()));
        }
        if (toPopulate instanceof IntegerType) {
            ((IntegerType) toPopulate).setValue(data.getValue());
        } else if (toPopulate instanceof Quantity) {
            ((Quantity) toPopulate).setValue(data.getValue());
        } else {
            populateIntegerTypeCrossVersion(toPopulate, data.getValue());
        }
    }

    private void populateTimeType(Object toPopulate, TimeType data) {
        if (toPopulate instanceof TimeType) {
            ((TimeType) toPopulate).setValue(data.getValue());
        } else if (toPopulate instanceof DateTimeType) {
            LocalDateTime localDateTime = LocalDateTime.of(LocalDate.now(),
                    LocalTime.of(data.getHour(), data.getMinute(),
                            (int) data.getSecond()));
            ((DateTimeType) toPopulate).setValue(Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant()));
        } else {
            populateTimeTypeCrossVersion(toPopulate, data.getValue(), data.getHour(), data.getMinute(), (int) data.getSecond());
        }
    }

    private void populateIdentifier(Object toPopulate, Identifier data, Terminology terminology) {
        final Coding translated = terminologyTranslator.translateToFhir(data.getValue(), data.getSystem(), null, terminology);
        if (translated != null) {
            if (StringUtils.isNotBlank(translated.getCode())) {
                data.setValue(translated.getCode());
            }
            if (StringUtils.isNotBlank(translated.getSystem())) {
                data.setSystem(translated.getSystem());
            }
        }
        if (toPopulate instanceof Identifier) {
            data.copyValues((Identifier) toPopulate);
        } else if (toPopulate instanceof StringType stringType) {
            stringType.setValue(data.getValue());
        } else {
            populateIdentifierCrossVersion(toPopulate, data.getValue(), data.getSystem());
        }
    }

    private void populateDateType(Object toPopulate, DateType data) {
        if (toPopulate instanceof DateType) {
            ((DateType) toPopulate).setValue(data.getValue());
        } else {
            populateDateTypeCrossVersion(toPopulate, data.getValue(), data.getValueAsString());
        }
    }

    private void populateCodeableConcept(Object toPopulate, CodeableConcept data, Terminology terminology) {
        data.getCoding().replaceAll(coding -> {
            final Coding translated = terminologyTranslator.translateToFhir(coding.getCode(), coding.getSystem(), null, terminology);
            return translated != null ? translated : coding;
        });
        if (toPopulate instanceof CodeableConcept) {
            data.copyValues((CodeableConcept) toPopulate);
        } else if (toPopulate instanceof Coding coding) {
            data.getCodingFirstRep().copyValues(coding);
        } else if (toPopulate instanceof Quantity q) {
            q.setValue(Long.parseLong(data.getText()));
            q.setCode(data.getCodingFirstRep().getCode());
            q.setUnit(data.getCodingFirstRep().getDisplay());
        } else if (toPopulate instanceof Enumeration<?>) {
            ((Enumeration<?>) toPopulate).setValueAsString(data.getCodingFirstRep().getCode());
        } else if (toPopulate instanceof Identifier identifier) {
            identifier.setSystem(data.getCodingFirstRep().getSystem());
            identifier.setValue(
                    data.getCodingFirstRep().getCode() == null ? data.getText() : data.getCodingFirstRep().getCode());
        } else {
            final String firstCode = data.getCodingFirstRep().getCode();
            final String firstSystem = data.getCodingFirstRep().getSystem();
            final String firstDisplay = data.getCodingFirstRep().getDisplay();
            final String text = data.getText();
            populateCodeableConceptCrossVersion(toPopulate, firstCode, firstSystem, firstDisplay, text);
        }
    }

    private void populateCoding(Object toPopulate, Coding data, Terminology terminology) {
        final Coding translated = terminologyTranslator.translateToFhir(data.getCode(), data.getSystem(), null, terminology);
        if (translated != null) {
            data = translated;
        }
        if (toPopulate instanceof Coding) {
            data.copyValues((Coding) toPopulate);
        } else if (toPopulate instanceof Enumeration<?>) {
            ((Enumeration<?>) toPopulate).setValueAsString(data.getCode());
        } else {
            populateCodingCrossVersion(toPopulate, data.getCode(), data.getSystem(), data.getDisplay());
        }
    }

    private void populateAttachment(Object toPopulate, Attachment data) {
        if (toPopulate instanceof Attachment) {
            data.copyValues((Attachment) toPopulate);
        } else {
            populateAttachmentCrossVersion(toPopulate, data.getContentType(), data.getData(), data.getUrl(), data.getTitle());
        }
    }

    private void populateStringType(Object toPopulate, StringType data, Terminology terminology) {
        final Coding translated = terminologyTranslator.translateToFhir(data.getValue(), null, null, terminology);
        final String value = translated != null && StringUtils.isNotBlank(translated.getCode())
                ? translated.getCode()
                : data.getValue();
        if (toPopulate instanceof Enumeration<?> enumeration) {
            enumeration.setValueAsString(value);
        } else if (toPopulate instanceof DateTimeType dateTimeType) {
            dateTimeType.setValueAsString(data.getValueAsString());
        } else if (toPopulate instanceof CodeableConcept codeableConcept) {
            codeableConcept.setText(data.getValue());
        } else if (toPopulate instanceof InstantType instantType) {
            instantType.setValueAsString(data.getValueAsString());
        } else if (toPopulate instanceof IntegerType integerType) {
            integerType.setValue(Integer.valueOf(data.getValue()));
        } else if (toPopulate instanceof BooleanType booleanType) {
            booleanType.setValue(Boolean.valueOf(data.getValue()));
        } else if (toPopulate instanceof PrimitiveType<?> primitiveType) {
            ((PrimitiveType<String>) primitiveType).setValue(value);
        } else if (toPopulate instanceof XhtmlNode xhtmlNode) {
            xhtmlNode.setValueAsString(value);
        } else {
            populateStringTypeCrossVersion(toPopulate, value, data.getValueAsString());
        }
    }

    private void populateBooleanType(Object toPopulate, BooleanType data) {
        if (toPopulate instanceof BooleanType) {
            ((BooleanType) toPopulate).setValue(data.getValue());
        } else {
            populateBooleanTypeCrossVersion(toPopulate, data.getValue());
        }
    }

    private void populatePeriodType(Object toPopulate, Period data) {
        if (toPopulate instanceof Period period) {
            data.copyValues(period);
        } else {
            populatePeriodTypeCrossVersion(toPopulate, data.getStart(), data.getEnd());
        }
    }

    // -------------------------------------------------------------------------
    // Cross-version helpers
    // Each helper receives only Java primitives / java.util types extracted from
    // the R4 source, and applies them to STU3 / R4B / R5 target objects.
    // -------------------------------------------------------------------------

    private void populateQuantityCrossVersion(final Object toPopulate,
                                              final BigDecimal value,
                                              final String code,
                                              final String system,
                                              final String unit) {
        if (toPopulate instanceof org.hl7.fhir.dstu3.model.Quantity q) {
            if (value != null) q.setValue(value);
            if (code != null) q.setCode(code);
            if (system != null) q.setSystem(system);
            if (unit != null) q.setUnit(unit);
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.IntegerType i) {
            if (value != null) i.setValue(value.intValue());
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.Ratio r) {
            final org.hl7.fhir.dstu3.model.Quantity num = new org.hl7.fhir.dstu3.model.Quantity();
            if (value != null) num.setValue(value);
            if (code != null) num.setCode(code);
            if (system != null) num.setSystem(system);
            if (unit != null) num.setUnit(unit);
            r.setNumerator(num);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.Quantity q) {
            if (value != null) q.setValue(value);
            if (code != null) q.setCode(code);
            if (system != null) q.setSystem(system);
            if (unit != null) q.setUnit(unit);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.IntegerType i) {
            if (value != null) i.setValue(value.intValue());
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.Ratio r) {
            final org.hl7.fhir.r4b.model.Quantity num = new org.hl7.fhir.r4b.model.Quantity();
            if (value != null) num.setValue(value);
            if (code != null) num.setCode(code);
            if (system != null) num.setSystem(system);
            if (unit != null) num.setUnit(unit);
            r.setNumerator(num);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.Quantity q) {
            if (value != null) q.setValue(value);
            if (code != null) q.setCode(code);
            if (system != null) q.setSystem(system);
            if (unit != null) q.setUnit(unit);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.IntegerType i) {
            if (value != null) i.setValue(value.intValue());
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.Ratio r) {
            final org.hl7.fhir.r5.model.Quantity num = new org.hl7.fhir.r5.model.Quantity();
            if (value != null) num.setValue(value);
            if (code != null) num.setCode(code);
            if (system != null) num.setSystem(system);
            if (unit != null) num.setUnit(unit);
            r.setNumerator(num);
        }
    }

    private void populateDateTimeCrossVersion(final Object toPopulate,
                                              final Date value,
                                              final String valueAsString) {
        if (toPopulate instanceof org.hl7.fhir.dstu3.model.DateTimeType dt) {
            if (value != null) dt.setValue(value); else dt.setValueAsString(valueAsString);
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.InstantType it) {
            if (value != null) it.setValue(value); else it.setValueAsString(valueAsString);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.DateTimeType dt) {
            if (value != null) dt.setValue(value); else dt.setValueAsString(valueAsString);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.InstantType it) {
            if (value != null) it.setValue(value); else it.setValueAsString(valueAsString);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.DateTimeType dt) {
            if (value != null) dt.setValue(value); else dt.setValueAsString(valueAsString);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.InstantType it) {
            if (value != null) it.setValue(value); else it.setValueAsString(valueAsString);
        }
    }

    private void populateIntegerTypeCrossVersion(final Object toPopulate, final Integer value) {
        if (toPopulate instanceof org.hl7.fhir.dstu3.model.IntegerType i) {
            if (value != null) i.setValue(value);
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.Quantity q) {
            if (value != null) q.setValue(value);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.IntegerType i) {
            if (value != null) i.setValue(value);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.Quantity q) {
            if (value != null) q.setValue(value);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.IntegerType i) {
            if (value != null) i.setValue(value);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.Quantity q) {
            if (value != null) q.setValue(value);
        }
    }

    private void populateTimeTypeCrossVersion(final Object toPopulate,
                                              final String timeValue,
                                              final int hour,
                                              final int minute,
                                              final int second) {
        final Date asDate = Date.from(
                LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute, second))
                             .atZone(ZoneId.systemDefault()).toInstant());
        if (toPopulate instanceof org.hl7.fhir.dstu3.model.TimeType t) {
            t.setValue(timeValue);
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.DateTimeType dt) {
            dt.setValue(asDate);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.TimeType t) {
            t.setValue(timeValue);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.DateTimeType dt) {
            dt.setValue(asDate);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.TimeType t) {
            t.setValue(timeValue);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.DateTimeType dt) {
            dt.setValue(asDate);
        }
    }

    private void populateIdentifierCrossVersion(final Object toPopulate,
                                                final String value,
                                                final String system) {
        if (toPopulate instanceof org.hl7.fhir.dstu3.model.Identifier id) {
            if (value != null) id.setValue(value);
            if (system != null) id.setSystem(system);
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.StringType s) {
            s.setValue(value);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.Identifier id) {
            if (value != null) id.setValue(value);
            if (system != null) id.setSystem(system);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.StringType s) {
            s.setValue(value);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.Identifier id) {
            if (value != null) id.setValue(value);
            if (system != null) id.setSystem(system);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.StringType s) {
            s.setValue(value);
        }
    }

    private void populateDateTypeCrossVersion(final Object toPopulate,
                                              final Date value,
                                              final String valueAsString) {
        if (toPopulate instanceof org.hl7.fhir.dstu3.model.DateType d) {
            if (value != null) d.setValue(value); else d.setValueAsString(valueAsString);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.DateType d) {
            if (value != null) d.setValue(value); else d.setValueAsString(valueAsString);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.DateType d) {
            if (value != null) d.setValue(value); else d.setValueAsString(valueAsString);
        }
    }

    private void populateCodeableConceptCrossVersion(final Object toPopulate,
                                                     final String firstCode,
                                                     final String firstSystem,
                                                     final String firstDisplay,
                                                     final String text) {
        if (toPopulate instanceof org.hl7.fhir.dstu3.model.CodeableConcept cc) {
            cc.getCodingFirstRep().setCode(firstCode).setSystem(firstSystem).setDisplay(firstDisplay);
            if (text != null) cc.setText(text);
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.Coding c) {
            c.setCode(firstCode).setSystem(firstSystem).setDisplay(firstDisplay);
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.Quantity q) {
            if (text != null) q.setValue(Long.parseLong(text));
            q.setCode(firstCode);
            q.setUnit(firstDisplay);
        } else if (toPopulate instanceof IBaseEnumeration<?> e) {
            e.setValueAsString(firstCode);
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.Identifier id) {
            id.setSystem(firstSystem);
            id.setValue(firstCode == null ? text : firstCode);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.CodeableConcept cc) {
            cc.getCodingFirstRep().setCode(firstCode).setSystem(firstSystem).setDisplay(firstDisplay);
            if (text != null) cc.setText(text);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.Coding c) {
            c.setCode(firstCode).setSystem(firstSystem).setDisplay(firstDisplay);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.Quantity q) {
            if (text != null) q.setValue(Long.parseLong(text));
            q.setCode(firstCode);
            q.setUnit(firstDisplay);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.Identifier id) {
            id.setSystem(firstSystem);
            id.setValue(firstCode == null ? text : firstCode);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.CodeableConcept cc) {
            cc.getCodingFirstRep().setCode(firstCode).setSystem(firstSystem).setDisplay(firstDisplay);
            if (text != null) cc.setText(text);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.Coding c) {
            c.setCode(firstCode).setSystem(firstSystem).setDisplay(firstDisplay);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.Quantity q) {
            if (text != null) q.setValue(Long.parseLong(text));
            q.setCode(firstCode);
            q.setUnit(firstDisplay);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.Identifier id) {
            id.setSystem(firstSystem);
            id.setValue(firstCode == null ? text : firstCode);
        }
    }

    private void populateCodingCrossVersion(final Object toPopulate,
                                            final String code,
                                            final String system,
                                            final String display) {
        if (toPopulate instanceof org.hl7.fhir.dstu3.model.Coding c) {
            if (code != null) c.setCode(code);
            if (system != null) c.setSystem(system);
            if (display != null) c.setDisplay(display);
        } else if (toPopulate instanceof IBaseEnumeration<?> e) {
            e.setValueAsString(code);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.Coding c) {
            if (code != null) c.setCode(code);
            if (system != null) c.setSystem(system);
            if (display != null) c.setDisplay(display);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.Coding c) {
            if (code != null) c.setCode(code);
            if (system != null) c.setSystem(system);
            if (display != null) c.setDisplay(display);
        }
    }

    private void populateAttachmentCrossVersion(final Object toPopulate,
                                                final String contentType,
                                                final byte[] dataBytes,
                                                final String url,
                                                final String title) {
        if (toPopulate instanceof org.hl7.fhir.dstu3.model.Attachment a) {
            if (contentType != null) a.setContentType(contentType);
            if (dataBytes != null) a.setData(dataBytes);
            if (url != null) a.setUrl(url);
            if (title != null) a.setTitle(title);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.Attachment a) {
            if (contentType != null) a.setContentType(contentType);
            if (dataBytes != null) a.setData(dataBytes);
            if (url != null) a.setUrl(url);
            if (title != null) a.setTitle(title);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.Attachment a) {
            if (contentType != null) a.setContentType(contentType);
            if (dataBytes != null) a.setData(dataBytes);
            if (url != null) a.setUrl(url);
            if (title != null) a.setTitle(title);
        }
    }

    private void populateStringTypeCrossVersion(final Object toPopulate,
                                                final String value,
                                                final String valueAsString) {
        if (toPopulate instanceof IBaseEnumeration<?> e) {
            e.setValueAsString(value);
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.DateTimeType dt) {
            dt.setValueAsString(valueAsString);
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.CodeableConcept cc) {
            cc.setText(value);
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.InstantType it) {
            it.setValueAsString(valueAsString);
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.IntegerType i) {
            if (value != null) i.setValue(Integer.valueOf(value));
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.BooleanType b) {
            b.setValue(Boolean.valueOf(value));
        } else if (toPopulate instanceof org.hl7.fhir.dstu3.model.PrimitiveType<?> pt) {
            ((org.hl7.fhir.dstu3.model.PrimitiveType<String>) pt).setValue(value);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.DateTimeType dt) {
            dt.setValueAsString(valueAsString);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.CodeableConcept cc) {
            cc.setText(value);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.InstantType it) {
            it.setValueAsString(valueAsString);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.IntegerType i) {
            if (value != null) i.setValue(Integer.valueOf(value));
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.BooleanType b) {
            b.setValue(Boolean.valueOf(value));
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.PrimitiveType<?> pt) {
            ((org.hl7.fhir.r4b.model.PrimitiveType<String>) pt).setValue(value);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.DateTimeType dt) {
            dt.setValueAsString(valueAsString);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.CodeableConcept cc) {
            cc.setText(value);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.InstantType it) {
            it.setValueAsString(valueAsString);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.IntegerType i) {
            if (value != null) i.setValue(Integer.valueOf(value));
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.BooleanType b) {
            b.setValue(Boolean.valueOf(value));
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.PrimitiveType<?> pt) {
            ((org.hl7.fhir.r5.model.PrimitiveType<String>) pt).setValue(value);
        } else if (toPopulate instanceof XhtmlNode xhtmlNode) {
            xhtmlNode.setValueAsString(value);
        }
    }

    private void populateBooleanTypeCrossVersion(final Object toPopulate, final Boolean value) {
        if (toPopulate instanceof org.hl7.fhir.dstu3.model.BooleanType b) {
            b.setValue(value);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.BooleanType b) {
            b.setValue(value);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.BooleanType b) {
            b.setValue(value);
        }
    }

    private void populatePeriodTypeCrossVersion(final Object toPopulate,
                                                final Date start,
                                                final Date end) {
        if (toPopulate instanceof org.hl7.fhir.dstu3.model.Period p) {
            if (start != null) p.setStart(start);
            if (end != null) p.setEnd(end);
        } else if (toPopulate instanceof org.hl7.fhir.r4b.model.Period p) {
            if (start != null) p.setStart(start);
            if (end != null) p.setEnd(end);
        } else if (toPopulate instanceof org.hl7.fhir.r5.model.Period p) {
            if (start != null) p.setStart(start);
            if (end != null) p.setEnd(end);
        }
    }

    private void populateTimingCrossVersion(final Object toPopulate,
                                            final Timing data,
                                            final Terminology terminology) {
        // Timing is complex — we only handle the common case of the target being an
        // Enumeration/code derived from the timing code's first coding.
        final String code = data.hasCode() && data.getCode().hasCoding()
                ? data.getCode().getCodingFirstRep().getCode()
                : null;
        if (code == null) {
            return;
        }
        if (toPopulate instanceof IBaseEnumeration<?> e) {
            e.setValueAsString(code);
        }
    }

}
