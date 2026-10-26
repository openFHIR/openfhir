package com.syntaric.openfhir.mapping.ips;

/**
 * Runs the assertions of {@link IpsBidirectionalTest} against the IPS mappings that dispatch each section to its own
 * sub-context via slotContext (FHIRconnect-mapping-lib, one generic SECTION.adhoc.v1 model plus one sub-context
 * per section), using the template shipped with those mappings.
 */
public class IpsSlotContextBidirectionalTest extends IpsBidirectionalTest {

    @Override
    protected String modelMappings() {
        return "/ips_slotcontext/";
    }

    @Override
    protected String contextMapping() {
        return "/ips_slotcontext/projects/org.openehr/IPS/IPS.context.yml";
    }

    /**
     * The template these mappings target names its sections "IPS ..." (freshEHR IPS template 1.0.0-alpha.26).
     */
    @Override
    protected String optLocation() {
        return "/ips_slotcontext/International Patient Summary.opt";
    }

    /**
     * ips.flat.json of the baseline, with the section ids of this template.
     */
    @Override
    protected String flatInput() {
        return "/ips_slotcontext/ips.flat.json";
    }

    /**
     * These mappings translate the DV_TEXT to the FHIR code instead of passing the text through.
     */
    @Override
    protected String expectedAllergyCategory() {
        return "food";
    }
}
