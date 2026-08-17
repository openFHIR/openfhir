package com.syntaric.openfhir.mapping.toopenehr;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.datavalues.quantity.datetime.DvDuration;
import java.time.Duration;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.threeten.extra.PeriodDuration;

public class FlatDurationComponentFixerTest {

    private static final String PATH = "composition/element/duration_value";

    private JsonObject flatWith(final String suffix, final Number value) {
        final JsonObject flat = new JsonObject();
        flat.addProperty(PATH + suffix, value);
        return flat;
    }

    private DvDuration decoded(final Duration duration) {
        final DvDuration dvDuration = new DvDuration();
        dvDuration.setValue(PeriodDuration.of(duration));
        return dvDuration;
    }

    @Test
    public void minuteComponentIsCorrectedFromInflatedHours() {
        final Map<Duration, Duration> corrections =
                FlatDurationComponentFixer.corruptedToIntended(flatWith("|minute", 30));

        Assert.assertEquals(Duration.ofMinutes(30), corrections.get(Duration.ofHours(30)));
    }

    @Test
    public void secondComponentIsCorrectedFromInflatedHours() {
        final Map<Duration, Duration> corrections =
                FlatDurationComponentFixer.corruptedToIntended(flatWith("|second", 45));

        Assert.assertEquals(Duration.ofSeconds(45), corrections.get(Duration.ofHours(45)));
    }

    @Test
    public void hourAndDayComponentsAreLeftAlone() {
        Assert.assertTrue(FlatDurationComponentFixer.corruptedToIntended(flatWith("|hour", 3)).isEmpty());
        Assert.assertTrue(FlatDurationComponentFixer.corruptedToIntended(flatWith("|day", 2)).isEmpty());
    }

    @Test
    public void mixedComponentsAreCombined() {
        final JsonObject flat = flatWith("|hour", 1);
        flat.addProperty(PATH + "|minute", 30);

        // the SDK would decode 1h + 30h = PT31H, the intended value is PT1H30M
        final Map<Duration, Duration> corrections = FlatDurationComponentFixer.corruptedToIntended(flat);

        Assert.assertEquals(Duration.ofMinutes(90), corrections.get(Duration.ofHours(31)));
    }

    @Test
    public void durationsAlsoWrittenCorrectlyElsewhereAreNotRewritten() {
        // one node asks for 30 minutes (decoded as PT30H), another legitimately asks for 30 hours
        final JsonObject flat = flatWith("|minute", 30);
        flat.addProperty("composition/other/duration_value|hour", 30);

        Assert.assertTrue(FlatDurationComponentFixer.corruptedToIntended(flat).isEmpty());
    }

    @Test
    public void fixRewritesTheDecodedValue() {
        final DvDuration dvDuration = decoded(Duration.ofHours(30));

        final boolean corrected = FlatDurationComponentFixer.correct(
                dvDuration, FlatDurationComponentFixer.corruptedToIntended(flatWith("|minute", 30)));

        Assert.assertTrue(corrected);
        Assert.assertEquals(PeriodDuration.of(Duration.ofMinutes(30)), dvDuration.getValue());
    }

    @Test
    public void unrelatedDurationsAreUntouched() {
        final DvDuration dvDuration = decoded(Duration.ofHours(12));

        Assert.assertFalse(FlatDurationComponentFixer.correct(
                dvDuration, FlatDurationComponentFixer.corruptedToIntended(flatWith("|minute", 30))));
        Assert.assertEquals(PeriodDuration.of(Duration.ofHours(12)), dvDuration.getValue());
    }

    @Test
    public void nestedDurationsAreWalked() {
        final com.nedap.archie.rm.datastructures.Element element = new com.nedap.archie.rm.datastructures.Element();
        element.setValue(decoded(Duration.ofHours(30)));

        Assert.assertEquals(1, FlatDurationComponentFixer.fix(element, flatWith("|minute", 30)));
        Assert.assertEquals(PeriodDuration.of(Duration.ofMinutes(30)), ((DvDuration) element.getValue()).getValue());
    }

    @Test
    public void emptyOrNullFlatIsHandled() {
        Assert.assertTrue(FlatDurationComponentFixer.corruptedToIntended(null).isEmpty());
        Assert.assertTrue(FlatDurationComponentFixer.corruptedToIntended(new JsonObject()).isEmpty());
        Assert.assertEquals(0, FlatDurationComponentFixer.fix(decoded(Duration.ofHours(30)), new JsonObject()));
    }
}
