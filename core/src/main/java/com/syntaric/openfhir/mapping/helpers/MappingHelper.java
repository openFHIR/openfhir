package com.syntaric.openfhir.mapping.helpers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import com.syntaric.openfhir.terminology.OfCoding;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.hl7.fhir.r4.model.Base;

@Data
public class MappingHelper {

    private String modelMetadataName;
    private String archetype;

    private String mappingName;
    private String generatingResourceType;

    private String resolveResourceType;

    private String originalFhirPath;
    private String originalOpenEhrPath;

    // this is really a whole model mapping thing, so it will be the same for all these mappings
    private String openEhrHierarchySplitFlatPath;
    private Condition preprocessorOpenEhrCondition;
    private List<Condition> preprocessorFhirConditions;

    private String fhir;
    private String fhirWithCondition;
    private String fullFhirPath;

    private String openEhr;
    private String fullOpenEhrPath;
    private String fullOpenEhrFlatPath;
    private String fullOpenEhrFlatPathWithMatchingRegex;
    private String flatPathPipeSuffix; // used primarily for fhir->openehr and has the flat path suffix appended to it

    private String detectedType; // when doing openehr->fhir, we can detect specific type based on incoming openehr composition
    private String hardcodedType;
    private List<String> possibleRmTypes;

    private String programmedMapping;

    private String manualOpenEhrValue;
    private String manualFhirValue;

    private String unidirectional;

    private List<Condition> fhirConditions;
    private List<Condition> openEhrConditions;

    boolean enteredFromSlotArchetypeLink;

    private Terminology terminology;
    private List<OfCoding> availableCodings;

    private List<MappingHelper> children;

    @JsonIgnore
    private Base generatingFhirResource;

    @JsonIgnore
    private Object generatingFhirRoot;
    private boolean useParentRoot;
    private boolean hasSlot;

    @JsonIgnore
    private Object generatingFhirBase;

    @JsonIgnore
    private List<DataWithIndex> extractedOpenEhrData;

    public List<MappingHelper> getChildren() {
        if(children == null) children = new ArrayList<>();
        return children;
    }

    public MappingHelper clone() {
        final MappingHelper clone = cloneWithFhirResourceAndRootIntact();
        clone.generatingFhirResource = null;
        clone.generatingFhirRoot = null;
        clone.generatingFhirBase = null;
        clone.extractedOpenEhrData = null;
        return clone;
    }

    public MappingHelper cloneWithFhirResourceAndRootIntact() {
        final MappingHelper clone = new MappingHelper();
        clone.modelMetadataName = this.modelMetadataName;
        clone.archetype = this.archetype;
        clone.mappingName = this.mappingName;
        clone.generatingResourceType = this.generatingResourceType;
        clone.resolveResourceType = this.resolveResourceType;
        clone.originalFhirPath = this.originalFhirPath;
        clone.originalOpenEhrPath = this.originalOpenEhrPath;
        clone.flatPathPipeSuffix = this.flatPathPipeSuffix;
        clone.openEhrHierarchySplitFlatPath = this.openEhrHierarchySplitFlatPath;
        clone.fhir = this.fhir;
        clone.hasSlot = this.hasSlot;
        clone.fhirWithCondition = this.fhirWithCondition;
        clone.fullFhirPath = this.fullFhirPath;
        clone.openEhr = this.openEhr;
        clone.programmedMapping = this.programmedMapping;
        clone.possibleRmTypes = this.possibleRmTypes;
        clone.fullOpenEhrPath = this.fullOpenEhrPath;
        clone.fullOpenEhrFlatPath = this.fullOpenEhrFlatPath;
        clone.fullOpenEhrFlatPathWithMatchingRegex = this.fullOpenEhrFlatPathWithMatchingRegex;
        clone.detectedType = this.detectedType;
        clone.hardcodedType = this.hardcodedType;
        clone.manualOpenEhrValue = this.manualOpenEhrValue;
        clone.manualFhirValue = this.manualFhirValue;
        clone.unidirectional = this.unidirectional;
        clone.enteredFromSlotArchetypeLink = this.enteredFromSlotArchetypeLink;
        clone.preprocessorOpenEhrCondition = this.preprocessorOpenEhrCondition;
        clone.preprocessorFhirConditions = this.preprocessorFhirConditions == null ? null : new ArrayList<>(this.preprocessorFhirConditions);
        clone.fhirConditions = this.fhirConditions == null ? null : new ArrayList<>(this.fhirConditions);
        clone.openEhrConditions = this.openEhrConditions == null ? null : new ArrayList<>(this.openEhrConditions);
        clone.children = this.children == null ? null : this.children.stream().map(MappingHelper::clone).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        clone.terminology = this.terminology;
        clone.availableCodings = this.availableCodings;
        clone.generatingFhirResource = this.generatingFhirResource;
        clone.generatingFhirRoot = this.generatingFhirRoot;
        clone.generatingFhirBase = this.generatingFhirBase;
        clone.extractedOpenEhrData = this.extractedOpenEhrData;
        clone.useParentRoot = this.useParentRoot;
        return clone;
    }
}
