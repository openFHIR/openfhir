package com.syntaric.openfhir.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syntaric.openfhir.OpenFhirAuthorizationContext;
import com.syntaric.openfhir.db.entity.OptEntity;
import com.syntaric.openfhir.db.repository.OptRepository;
import com.syntaric.openfhir.producers.UserContextProducerInterface;
import com.syntaric.openfhir.util.OpenEhrTemplateUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for the creation and update dates an operational template upsert stores and returns.
 */
public class OptServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String OPT_ID = "opt-1";
    private static final Date CREATED = new Date(1_000_000L);

    private OptRepository optRepository;
    private OptService service;
    private String opt;

    @BeforeEach
    public void setUp() throws IOException {
        optRepository = mock(OptRepository.class);
        when(optRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        final OpenFhirAuthorizationContext authContext = new OpenFhirAuthorizationContext();
        authContext.setTenant(TENANT);
        authContext.setUserId("user-1");
        final UserContextProducerInterface userContextProducer = mock(UserContextProducerInterface.class);
        when(userContextProducer.getAuthContext()).thenReturn(authContext);

        service = new OptService(optRepository, new OpenEhrTemplateUtils(), userContextProducer);
        opt = IOUtils.toString(getClass().getResourceAsStream("/blood_pressure/Blood Pressure.opt"),
                               StandardCharsets.UTF_8);
    }

    @Test
    public void createSetsCreatedAndUpdated() {
        service.upsert(opt, null, "req-1");

        final OptEntity saved = savedEntity();
        assertNull(saved.getId());
        assertNotNull(saved.getCreated());
        assertEquals(saved.getCreated(), saved.getUpdated());
    }

    @Test
    public void updateKeepsCreatedAndSetsUpdated() {
        when(optRepository.findByIdAndOrganisation(OPT_ID, TENANT)).thenReturn(existing(CREATED));

        service.upsert(opt, OPT_ID, "req-1");

        final OptEntity saved = savedEntity();
        assertEquals(OPT_ID, saved.getId());
        assertEquals(CREATED, saved.getCreated());
        assertTrue(saved.getUpdated().after(CREATED));
    }

    @Test
    public void updateOfAnOptWithoutCreatedKeepsItWithout() {
        when(optRepository.findByIdAndOrganisation(OPT_ID, TENANT)).thenReturn(existing(null));

        service.upsert(opt, OPT_ID, "req-1");

        final OptEntity saved = savedEntity();
        assertEquals(OPT_ID, saved.getId());
        assertNull(saved.getCreated());
        assertNotNull(saved.getUpdated());
    }

    @Test
    public void theAnswerCarriesTheStoredDatesButNotTheContent() {
        when(optRepository.findByIdAndOrganisation(OPT_ID, TENANT)).thenReturn(existing(CREATED));

        final OptEntity answered = service.upsert(opt, OPT_ID, "req-1");

        final OptEntity saved = savedEntity();
        assertEquals(CREATED, answered.getCreated());
        assertEquals(saved.getUpdated(), answered.getUpdated());
        assertEquals("redacted", answered.getContent());
    }

    @Test
    public void idTheTenantDoesNotOwnCreatesANewOpt() {
        service.upsert(opt, "someone-elses-id", "req-1");

        final OptEntity saved = savedEntity();
        assertNull(saved.getId());
        assertNotNull(saved.getCreated());
    }

    private OptEntity existing(final Date created) {
        final OptEntity existing = new OptEntity();
        existing.setId(OPT_ID);
        existing.setCreated(created);
        return existing;
    }

    private OptEntity savedEntity() {
        final ArgumentCaptor<OptEntity> captor = ArgumentCaptor.forClass(OptEntity.class);
        verify(optRepository).save(captor.capture());
        return captor.getValue();
    }
}
