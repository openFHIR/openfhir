package com.syntaric.openfhir.mapping.ips;

/**
 * Runs the assertions of {@link DiscreteIpsToFhirTest} against the IPS mappings that dispatch each section to its own
 * sub-context via slotContext (FHIRconnect-mapping-lib, one generic SECTION.adhoc.v1 model plus one sub-context
 * per section), using the template shipped with those mappings.
 */
public class DiscreteIpsSlotContextToFhirTest extends DiscreteIpsToFhirTest {

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
     * This template has no diagnostic status (items[at0004]) in the problem qualifier cluster.
     */
    @Override
    protected String unresolvedMapping() {
        return "statusCoded";
    }

    @Override
    protected String unresolvedSegment() {
        return "items[at0004]";
    }
}
