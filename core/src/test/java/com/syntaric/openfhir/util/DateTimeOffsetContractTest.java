package com.syntaric.openfhir.util;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.terminology.NoOpTerminologyTranslator;
import org.hl7.fhir.r4.model.DateTimeType;
import org.junit.jupiter.api.Test;

/**
 * The timezone contract for date/time values, in both directions:
 *
 * <p><b>Preserve an offset when the source has one; do not invent one when it does not.</b>
 *
 * <p>Offsets are preserved <em>as written</em>. {@code Z} stays {@code Z}, {@code +00:00} stays
 * {@code +00:00} (the two denote the same instant but are different strings, and normalising either
 * way would make the round trip inexact), and {@code +01:00} stays {@code +01:00} with its original
 * wall-clock reading.
 *
 * <p>What this rules out is the previous behaviour, which went through {@code java.util.Date} — a
 * bare instant with no zone — and re-rendered the result in the server's default zone. That did two
 * wrong things at once: it dropped the offset the source was authored with, and it <em>shifted the
 * wall clock</em>, so {@code 09:15:00+01:00} became {@code 10:15:00+02:00} on a machine in
 * {@code Europe/Ljubljana}. The same input mapped on two servers in different zones produced two
 * different readings, and neither matched the source. openEHR's DV_DATE_TIME carries its own offset,
 * so neither survived a round trip.
 */
class DateTimeOffsetContractTest {

    // ── openEHR → FHIR ─────────────────────────────────────────────────────────

    @Test
    void toFhir_preservesOffsetsExactlyAsWritten() {
        assertSoftly(softly -> {
            softly.assertThat(toFhir("2026-08-17T09:15:00+01:00")).isEqualTo("2026-08-17T09:15:00+01:00");
            softly.assertThat(toFhir("2026-08-17T09:15:00Z")).isEqualTo("2026-08-17T09:15:00Z");
            softly.assertThat(toFhir("2026-08-17T09:15:00+00:00")).isEqualTo("2026-08-17T09:15:00+00:00");
            softly.assertThat(toFhir("2026-08-17T09:15:00-05:00")).isEqualTo("2026-08-17T09:15:00-05:00");
        });
    }

    /**
     * The case that motivated the whole contract: a zone-less source must not acquire the server's
     * offset. Previously this returned '...+01:00' (or whatever the host happened to be set to).
     */
    @Test
    void toFhir_doesNotInventAnOffsetForAZonelessSource() {
        assertSoftly(softly -> {
            softly.assertThat(toFhir("2026-08-17T09:15:00")).isEqualTo("2026-08-17T09:15:00");
            softly.assertThat(toFhir("2022-02-03T04:05:06")).isEqualTo("2022-02-03T04:05:06");
        });
    }

    /** A date-only value has no time and therefore no offset; its precision must survive. */
    @Test
    void toFhir_leavesDateOnlyValuesAlone() {
        assertSoftly(softly -> {
            softly.assertThat(toFhir("1993-09-08")).isEqualTo("1993-09-08");
            softly.assertThat(toFhir("2026-08")).isEqualTo("2026-08");
        });
    }

    // ── FHIR → openEHR ─────────────────────────────────────────────────────────

    @Test
    void toOpenEhr_preservesOffsetsExactlyAsWritten() {
        assertSoftly(softly -> {
            softly.assertThat(toOpenEhr("2026-08-17T09:15:00+01:00")).isEqualTo("2026-08-17T09:15:00+01:00");
            softly.assertThat(toOpenEhr("2026-08-17T09:15:00Z")).isEqualTo("2026-08-17T09:15:00Z");
            softly.assertThat(toOpenEhr("2026-08-17T09:15:00+00:00")).isEqualTo("2026-08-17T09:15:00+00:00");
            softly.assertThat(toOpenEhr("2026-08-17T09:15:00-05:00")).isEqualTo("2026-08-17T09:15:00-05:00");
        });
    }

    /**
     * A UTC instant must not be silently converted into the server's local reading. This previously
     * returned '10:30:00' on a +01:00 host — right instant, wrong reading, and the offset gone.
     */
    @Test
    void toOpenEhr_doesNotShiftAUtcInstantIntoServerLocalTime() {
        assertSoftly(softly -> {
            softly.assertThat(toOpenEhr("2024-08-22T08:30:00Z")).isEqualTo("2024-08-22T08:30:00Z");
            softly.assertThat(toOpenEhr("2025-02-03T04:05:06Z")).isEqualTo("2025-02-03T04:05:06Z");
        });
    }

    @Test
    void toOpenEhr_doesNotInventAnOffsetForAZonelessSource() {
        assertSoftly(softly -> {
            softly.assertThat(toOpenEhr("2026-08-17T09:15:00")).isEqualTo("2026-08-17T09:15:00");
            softly.assertThat(toOpenEhr("2022-02-03T04:05:06")).isEqualTo("2022-02-03T04:05:06");
        });
    }

    // ── round trip ─────────────────────────────────────────────────────────────

    /**
     * The property that matters in practice: whatever an openEHR composition was authored with comes
     * back byte-identical, regardless of the zone the mapping happens to run in.
     */
    @Test
    void roundTripIsExactForEveryOffsetForm() {
        assertSoftly(softly -> {
            for (final String value : new String[] {
                    "2026-08-17T09:15:00+01:00",
                    "2026-08-17T09:15:00Z",
                    "2026-08-17T09:15:00+00:00",
                    "2026-08-17T09:15:00-05:00",
                    "2026-08-17T09:15:00",
            }) {
                softly.assertThat(toOpenEhr(toFhir(value)))
                        .as("round trip of %s", value)
                        .isEqualTo(value);
            }
        });
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** openEHR flat value in, the FHIR dateTime's lexical form out. */
    private String toFhir(final String openEhrValue) {
        final DateTimeType target = new DateTimeType();
        new FhirInstancePopulator(new NoOpPrePostFhirInstancePopulator(), new NoOpTerminologyTranslator())
                .handleSpecificTypePopulation(target, new DateTimeType(openEhrValue), null);
        return target.getValueAsString();
    }

    /** FHIR dateTime in, the openEHR flat value out. */
    private String toOpenEhr(final String fhirValue) {
        final JsonObject flat = new JsonObject();
        new OpenEhrPopulator(new OpenFhirMapperUtils(), null, new NoOpPrePostOpenEhrPopulator(),
                             new OpenFhirStringUtils())
                .setOpenEhrValue(null, "test/time", new DateTimeType(fhirValue),
                                 FhirConnectConst.DV_DATE_TIME, false, flat, null, null);
        return flat.get("test/time").getAsString();
    }
}
