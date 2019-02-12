package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.FPHSExternalIdentifierDao;
import org.sagebionetworks.bridge.dynamodb.DynamoFPHSExternalIdentifier;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class FPHSServiceTest {

    private FPHSService service;
    @Mock
    private FPHSExternalIdentifierDao mockDao;
    @Mock
    private AccountDao mockAccountDao;
    @Mock
    private Account mockAccount;
    
    private ExternalIdentifier externalId;
    
    @Before
    public void before() {
        externalId = ExternalIdentifier.create(TEST_STUDY, "gar");
        service = new FPHSService();
        service.setFPHSExternalIdentifierDao(mockDao);
        service.setAccountDao(mockAccountDao);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void validateIdThrowsException() throws Exception {
        service.verifyExternalIdentifier(ExternalIdentifier.create(TEST_STUDY, ""));
    }
    
    @Test
    public void verifyExternalIdentifierSucceeds() throws Exception {
        service.verifyExternalIdentifier(externalId);
        verify(mockDao).verifyExternalId(externalId);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void verifyExternalIdentifierFailsOnNotFound() throws Exception {
        doThrow(new EntityNotFoundException(ExternalIdentifier.class)).when(mockDao).verifyExternalId(externalId);
        
        service.verifyExternalIdentifier(externalId);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void registerIdThrowsException() throws Exception {
        service.registerExternalIdentifier(TEST_STUDY, "BBB", ExternalIdentifier.create(TEST_STUDY, null));
    }
    
    @Test
    public void registerExternalIdentifier() throws Exception {
        TestUtils.mockEditAccount(mockAccountDao, mockAccount);
        Set<String> dataGroups = Sets.newHashSet();
        when(mockAccount.getDataGroups()).thenReturn(dataGroups);
        
        service.registerExternalIdentifier(TEST_STUDY, "BBB", externalId);
        verify(mockDao).registerExternalId(externalId);
        verify(mockAccount).setExternalId(externalId.getIdentifier());
        assertEquals(Sets.newHashSet("football_player"), dataGroups);
    }
    
    @Test
    public void failureOfDaoDoesNotSetExternalId() throws Exception {
        // Mock this, throw exception afterward
        doThrow(new EntityNotFoundException(ExternalIdentifier.class, "Not found")).when(mockDao).registerExternalId(externalId);
        try {
            service.registerExternalIdentifier(TEST_STUDY, "BBB", externalId);
            fail("Exception should have been thrown");
        } catch(EntityNotFoundException e) {
            verify(mockDao).verifyExternalId(externalId);
            verify(mockDao).registerExternalId(externalId);
            verifyNoMoreInteractions(mockDao);
            verifyNoMoreInteractions(mockAccountDao);
        }
    }
    
    @Test
    public void failureToSetExternalIdRollsBackRegistration() throws Exception {
        doThrow(new RuntimeException()).when(mockDao).verifyExternalId(any());
        try {
            service.registerExternalIdentifier(TEST_STUDY, "BBB", externalId);
            fail("Exception should have been thrown");
        } catch(RuntimeException e) {
            verify(mockDao).verifyExternalId(externalId);
            verifyNoMoreInteractions(mockDao);
            verifyNoMoreInteractions(mockAccountDao);
        }
    }
    
    @Test
    public void getExternalIdentifiers() throws Exception {
        List<FPHSExternalIdentifier> externalIds = Lists.newArrayList(
                new DynamoFPHSExternalIdentifier("foo"), new DynamoFPHSExternalIdentifier("bar"));
        when(mockDao.getExternalIds()).thenReturn(externalIds);
        
        List<FPHSExternalIdentifier> identifiers = service.getExternalIdentifiers();
        
        assertEquals(externalIds, identifiers);
        verify(mockDao).getExternalIds();
    }
    
    @Test
    public void addExternalIdentifiers() throws Exception {
        List<FPHSExternalIdentifier> identifiers = Lists.newArrayList(FPHSExternalIdentifier.create("AAA"),
                FPHSExternalIdentifier.create("BBB"), FPHSExternalIdentifier.create("CCC"));
        
        service.addExternalIdentifiers(identifiers);
        verify(mockDao).addExternalIds(identifiers);
    }
    
}
