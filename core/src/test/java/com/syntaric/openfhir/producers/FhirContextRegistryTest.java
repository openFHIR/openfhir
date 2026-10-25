package com.syntaric.openfhir.producers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.uhn.fhir.fhirpath.IFhirPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Building a FHIRPath engine parses every StructureDefinition of its FHIR version. A cold instance hit by a burst
 * of concurrent requests must build it once and share it, not once per request thread — the latter ran a 2 GB heap
 * out of memory under 200 concurrent users.
 */
class FhirContextRegistryTest {

    @Test
    void concurrentFirstAccess_buildsOneSharedEngine() throws Exception {
        final FhirContextRegistry registry = new FhirContextRegistry();
        final int threads = 16;
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            final List<Future<IFhirPath>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return registry.getDefaultFhirPath();
                }));
            }
            start.countDown();

            final Set<IFhirPath> distinct = ConcurrentHashMap.newKeySet();
            for (final Future<IFhirPath> result : results) {
                distinct.add(result.get());
            }
            assertEquals(1, distinct.size());
        } finally {
            pool.shutdownNow();
        }
    }
}
