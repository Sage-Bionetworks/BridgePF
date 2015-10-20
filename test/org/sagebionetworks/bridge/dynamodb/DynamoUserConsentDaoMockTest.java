package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;

public class DynamoUserConsentDaoMockTest {

    private static final StudyIdentifier STUDY_IDENTIFIER = new StudyIdentifierImpl("test-study");
    
    DynamoUserConsentDao userConsentDao;
    
    DynamoStudyConsent1 studyConsent;
    
    DynamoDBMapper mapper2;
    
    DynamoDBMapper mapper3;
    
    @Before
    public void before() {
        studyConsent = new DynamoStudyConsent1();
        studyConsent.setActive(true);
        studyConsent.setCreatedOn(DateTime.now().getMillis());
        studyConsent.setStudyKey("test-study");
        studyConsent.setStoragePath("test-study.12341234123");
        
        mapper2 = mock(DynamoDBMapper.class);
        mapper3 = mock(DynamoDBMapper.class);
        
        createQueryResults(null, null);
        
        userConsentDao = new DynamoUserConsentDao();
        userConsentDao.setDdbMapper2(mapper2);
        userConsentDao.setDdbMapper3(mapper3);
    }
    
    @SuppressWarnings("unchecked")
    private void createQueryResults(DynamoUserConsent2 consent2, DynamoUserConsent3 consent3) {
        PaginatedQueryList<DynamoUserConsent3> list3 = mock(PaginatedQueryList.class);
        if (consent2 == null) {
            when(list3.isEmpty()).thenReturn(true);
        } else {
            when(list3.isEmpty()).thenReturn(false);
            when(list3.get(0)).thenReturn(consent3);
        }
        doReturn(consent2).when(mapper2).load(any());
        doReturn(list3).when(mapper3).query(any(), any());
    }
    
    private void createQueryWithResults() {
        DynamoUserConsent2 existingConsent2 = new DynamoUserConsent2("AAA", "test-study");
        existingConsent2.setConsentCreatedOn(studyConsent.getCreatedOn());
        existingConsent2.setSignedOn(DateTime.now().getMillis());

        DynamoUserConsent3 existingConsent3 = new DynamoUserConsent3();
        existingConsent3.setHealthCodeStudy("AAA", "test-study");
        existingConsent3.setConsentCreatedOn(studyConsent.getCreatedOn());
        existingConsent3.setSignedOn(DateTime.now().getMillis());
        
        createQueryResults(existingConsent2, existingConsent3);
    }
    
    @Test
    public void consentsWrittenToBothTables() {
        userConsentDao.giveConsent("AAA", studyConsent);
        
        ArgumentCaptor<DynamoUserConsent2> argCaptor2 = ArgumentCaptor.forClass(DynamoUserConsent2.class);
        ArgumentCaptor<DynamoUserConsent3> argCaptor3 = ArgumentCaptor.forClass(DynamoUserConsent3.class);
        
        verify(mapper2).load(any());
        verify(mapper2).save(argCaptor2.capture());
        verify(mapper3).query(any(), any());
        verify(mapper3).save(argCaptor3.capture());
        verifyNoMoreInteractions(mapper2);
        verifyNoMoreInteractions(mapper3);
        
        DynamoUserConsent2 cons2 = argCaptor2.getValue();
        DynamoUserConsent3 cons3 = argCaptor3.getValue();
        
        assertEquals(studyConsent.getCreatedOn(), cons2.getConsentCreatedOn());
        assertTrue(cons2.getSignedOn() > 0L);
        assertEquals("AAA", cons2.getHealthCode());
        assertEquals("AAA:test-study", cons2.getHealthCodeStudy());
        
        assertEquals(cons2.getConsentCreatedOn(), cons3.getConsentCreatedOn());
        assertEquals((Long)cons2.getSignedOn(), (Long)cons3.getSignedOn());
        assertEquals(cons2.getHealthCode(), cons3.getHealthCode());
        assertEquals(cons2.getHealthCodeStudy(), cons3.getHealthCodeStudy());
        assertEquals(cons2.getStudyIdentifier(), cons3.getStudyIdentifier());
    }
    
    @Test
    public void throwExceptionIfConsentExists() {
        DynamoUserConsent2 existingConsent = new DynamoUserConsent2("AAA", "test-study");
        existingConsent.setConsentCreatedOn(studyConsent.getCreatedOn());
        existingConsent.setSignedOn(DateTime.now().getMillis());

        when(mapper2.load(any())).thenReturn(existingConsent);

        try {
            userConsentDao.giveConsent("AAA", studyConsent);
            fail("Should have thrown exception");
        } catch(EntityAlreadyExistsException e) {
        }
        verify(mapper2).load(any());
        verifyNoMoreInteractions(mapper2);
        verifyNoMoreInteractions(mapper3);
    }
    
    @Test
    public void withdrawUpdatesBothTables() {
        createQueryWithResults();
        
        userConsentDao.withdrawConsent("AAA", STUDY_IDENTIFIER);
        
        verify(mapper2).load(any());
        verify(mapper2).delete(any(DynamoUserConsent2.class));
        verify(mapper3).query(any(), any());
        verify(mapper3).delete(any(DynamoUserConsent3.class));
        verifyNoMoreInteractions(mapper2);
        verifyNoMoreInteractions(mapper3);
    }
    
    @Test
    public void getConsentFromOriginalTableOnly() {
        createQueryWithResults();
        
        UserConsent consent = userConsentDao.getUserConsent("AAA", STUDY_IDENTIFIER);
        assertTrue(consent instanceof DynamoUserConsent2);
        assertEquals("test-study", consent.getStudyIdentifier());
        
        verify(mapper2).load(any());
        verifyNoMoreInteractions(mapper2);
        verifyNoMoreInteractions(mapper3);
    }
    
    @Test
    public void testIfConsentedFromOriginalTable() {
        createQueryWithResults();
        
        userConsentDao.hasConsented("AAA", STUDY_IDENTIFIER);
        
        verify(mapper2).load(any());
        verifyNoMoreInteractions(mapper2);
        verifyNoMoreInteractions(mapper3);
    }
}
