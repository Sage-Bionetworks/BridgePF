package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dao.FPHSExternalIdentifierDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;

import com.newrelic.agent.deps.com.google.common.collect.Lists;

public class FPHSServiceTest {

    private FPHSService service;
    private FPHSExternalIdentifierDao dao;
    private ParticipantOptionsService optionsService;
    private ExternalIdentifier externalId;
    
    @Before
    public void before() {
        externalId =  new ExternalIdentifier("gar");
        service = new FPHSService();
        dao = mock(FPHSExternalIdentifierDao.class);
        optionsService = mock(ParticipantOptionsService.class);
        
        service.setFPHSExternalIdentifierDao(dao); 
        service.setParticipantOptionsService(optionsService);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void validateIdThrowsException() throws Exception {
        service.verifyExternalIdentifier(new ExternalIdentifier(""));
    }
    
    @Test(expected = InvalidEntityException.class)
    public void registerIdThrowsException() throws Exception {
        service.registerExternalIdentifier(TEST_STUDY, "BBB", new ExternalIdentifier(null));
    }
    
    @Test
    public void verifyExternalIdentifierFalse() throws Exception {
        when(dao.verifyExternalId(externalId)).thenReturn(false);
        assertFalse(service.verifyExternalIdentifier(externalId));
    }

    @Test
    public void verifyExternalIdentifierTrue() throws Exception {
        when(dao.verifyExternalId(externalId)).thenReturn(true);
        assertTrue(service.verifyExternalIdentifier(externalId));
    }
    
    @Test
    public void registerExternalIdentifier() throws Exception {
        service.registerExternalIdentifier(TEST_STUDY, "BBB", externalId);
        verify(dao).registerExternalId(externalId);
    }
    
    @Test
    public void failureOfDaoDoeNotSetExternalId() throws Exception {
        doThrow(new EntityNotFoundException(ExternalIdentifier.class, "Not found")).when(dao).registerExternalId(externalId);
        try {
            service.registerExternalIdentifier(TEST_STUDY, "BBB", externalId);
            fail("Exception should have been thrown");
        } catch(EntityNotFoundException e) {
            verify(dao).registerExternalId(externalId);
            verifyNoMoreInteractions(dao);
            verifyNoMoreInteractions(optionsService);
        }
    }
    
    @Test
    public void failureToSetExternalIdRollsBackRegistration() throws Exception {
        doThrow(new RuntimeException()).when(optionsService).setOption(TEST_STUDY, "BBB", 
                EXTERNAL_IDENTIFIER, externalId.getIdentifier());
        try {
            service.registerExternalIdentifier(TEST_STUDY, "BBB", externalId);
            fail("Exception should have been thrown");
        } catch(RuntimeException e) {
            verify(dao).registerExternalId(externalId);
            verify(dao).unregisterExternalId(externalId);
            verifyNoMoreInteractions(dao);
            verify(optionsService).setOption(TEST_STUDY, "BBB", EXTERNAL_IDENTIFIER,
                    externalId.getIdentifier());
            verifyNoMoreInteractions(optionsService);
        }
    }
    
    @Test
    public void getExternalIdentifiers() throws Exception {
        service.getExternalIdentifiers();
        verify(dao).getExternalIds();
    }
    
    @Test
    public void addExternalIdentifiers() throws Exception {
        List<FPHSExternalIdentifier> identifiers = Lists.newArrayList(FPHSExternalIdentifier.create("AAA"),
                FPHSExternalIdentifier.create("BBB"), FPHSExternalIdentifier.create("CCC"));
        
        service.addExternalIdentifiers(identifiers);
        verify(dao).addExternalIds(identifiers);
    }
    
}
