package com.syntaric.openfhir.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * {@link OpenFhirMapperUtils} is a singleton shared by every request thread. Parsing and formatting dates
 * concurrently must give each caller its own result — shared {@code SimpleDateFormat}s failed requests with
 * {@code NumberFormatException} and, worse, silently returned another thread's date.
 */
class OpenFhirMapperUtilsDateConcurrencyTest {

    private static final int THREADS = 16;
    private static final int ITERATIONS = 2_000;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final OpenFhirMapperUtils utils = new OpenFhirMapperUtils();

    @Test
    void concurrentParseAndFormat_eachCallerGetsItsOwnValue() throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            final List<Future<Integer>> mismatches = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int thread = t;
                mismatches.add(pool.submit(() -> {
                    start.await();
                    int wrong = 0;
                    for (int i = 0; i < ITERATIONS; i++) {
                        // a distinct instant per thread and iteration, so a value leaking between threads shows;
                        // hours from 04:00 keep clear of DST gaps, where a local time does not exist
                        final LocalDateTime local = LocalDateTime.of(2000 + thread, 1 + i % 12, 1 + i % 28,
                                                                     4 + i % 20, i % 60, thread);
                        final Date date = Date.from(local.atZone(ZoneId.systemDefault()).toInstant());
                        final String dateTime = DATE_TIME.format(local);
                        try {
                            if (!date.equals(utils.stringToDate(dateTime))
                                    || !dateTime.equals(utils.dateTimeToString(date))
                                    || !dateTime.substring(0, 10).equals(utils.dateToString(date))
                                    || !dateTime.substring(11).equals(utils.timeToString(date))) {
                                wrong++;
                            }
                        } catch (final RuntimeException e) {
                            wrong++;
                        }
                    }
                    return wrong;
                }));
            }
            start.countDown();

            int total = 0;
            for (final Future<Integer> result : mismatches) {
                total += result.get();
            }
            assertEquals(0, total, "dates parsed or formatted wrongly under concurrent use");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void stringToDate_keepsItsLenientSemantics() {
        final ZoneId zone = ZoneId.systemDefault();

        // date-only input falls back to the date pattern, at local midnight
        assertEquals(Date.from(LocalDateTime.of(2024, 3, 5, 0, 0).atZone(zone).toInstant()),
                     utils.stringToDate("2024-03-05"));
        // whatever follows the seconds (fraction, offset) is ignored, as before
        assertEquals(Date.from(LocalDateTime.of(2024, 3, 5, 10, 20, 30).atZone(zone).toInstant()),
                     utils.stringToDate("2024-03-05T10:20:30.123+02:00"));
        assertEquals(null, utils.stringToDate("not a date"));
        assertEquals(null, utils.stringToDate(null));
    }
}
