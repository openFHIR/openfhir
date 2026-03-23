package com.syntaric.openfhir.util;

import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.terminology.TerminologyTranslatorInterface;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseDatatype;
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
 * Used for populating a FHIR Base
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
        }
        if (toPopulate instanceof TimingRepeatComponent timingRepeatComponent) {
            final TimingRepeatComponent repeat = data.getRepeat();
            repeat.copyValues(timingRepeatComponent);
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
        }
    }

    private void populateDateTime(Object toPopulate, DateTimeType data) {
        if (toPopulate instanceof DateTimeType) {
            ((DateTimeType) toPopulate).setValue(data.getValue());
        } else if (toPopulate instanceof InstantType) {
            ((InstantType) toPopulate).setValue(data.getValue());
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
        }
    }

    private void populateDateType(Object toPopulate, DateType data) {
        if (toPopulate instanceof DateType) {
            ((DateType) toPopulate).setValue(data.getValue());
        }
    }

    private void populateCodeableConcept(Object toPopulate, CodeableConcept data, Terminology terminology) {
        data.getCoding().replaceAll(coding -> {
            final Coding translated = terminologyTranslator.translateToFhir(coding.getCode(), coding.getSystem(), null, terminology);
            return translated != null ? translated : coding;
        });
        if (toPopulate instanceof CodeableConcept) {
            data.copyValues((CodeableConcept) toPopulate);
        }
        if (toPopulate instanceof Coding coding) {
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
        }
    }

    private void populateAttachment(Object toPopulate, Attachment data) {
        if (toPopulate instanceof Attachment) {
            data.copyValues((Attachment) toPopulate);
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
        }
    }

    private void populateBooleanType(Object toPopulate, BooleanType data) {
        if (toPopulate instanceof BooleanType) {
            ((BooleanType) toPopulate).setValue(data.getValue());
        }
    }

    private void populatePeriodType(Object toPopulate, Period data) {
        if (toPopulate instanceof Period period) {
            data.copyValues(period);
        }
    }

}
