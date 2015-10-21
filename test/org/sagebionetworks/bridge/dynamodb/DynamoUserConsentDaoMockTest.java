package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.stream.Stream;

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
        PaginatedQueryList<DynamoUserConsent3> pqList = mock(PaginatedQueryList.class);
        if (consent2 == null) {
            when(pqList.isEmpty()).thenReturn(true);
        } else {
            when(pqList.isEmpty()).thenReturn(false);
            when(pqList.get(0)).thenReturn(consent3);
            when(pqList.stream()).thenReturn(Stream.of(consent3));
        }
        doReturn(consent2).when(mapper2).load(any());
        doReturn(pqList).when(mapper3).query(any(), any());
    }
    
    private void createQueryWithResults() {
        DynamoUserConsent2 existingConsent2 = new DynamoUserConsent2("AAA", "test-study");
        existingConsent2.setConsentCreatedOn(studyConsent.getCreatedOn());
        existingConsent2.setSignedOn(DateTime.now().getMillis());

        DynamoUserConsent3 existingConsent3 = new DynamoUserConsent3("AAA", "test-study");
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
            assertNull(e.getEntity());
            assertEquals("Consent already exists.", e.getMessage());
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
    
    @Test
    public void saveFirstTableThrowsErrorSecondTableIgnored() {
        doThrow(new ConcurrentModificationException()).when(mapper3).save(any());
        
        // If mapper3 throws exception on save, do not call mapper2, and do propagate exception
        try {
            userConsentDao.giveConsent("AAA", studyConsent);
            fail("Should have thrown exception");
        } catch(ConcurrentModificationException e) {
            verify(mapper2).load(any());
            verify(mapper3).query(any(), any());
            verify(mapper3).save(any());
            // No mapper 2 save, 3 failed
            verifyNoMoreInteractions(mapper3);
            verifyNoMoreInteractions(mapper2);
        }
    }
    
    @Test
    public void saveSecondTableThrowsErrorFirstUndone() {
        doThrow(new ConcurrentModificationException()).when(mapper2).save(any());
        
        // If mapper2 throws exception on save, undo save to mapper3, then propagate exception 
        try {
            userConsentDao.giveConsent("AAA", studyConsent);
            fail("Should have thrown exception");
        } catch(ConcurrentModificationException e) {
            verify(mapper2).load(any());
            // saves successfully
            verify(mapper3).query(any(), any());
            verify(mapper3).save(any());
            // but two throws an error, so 3 deletes
            verify(mapper2).save(any());
            verify(mapper3).delete(any());
            verifyNoMoreInteractions(mapper3);
            verifyNoMoreInteractions(mapper2);
        }
    }
    
    @Test
    public void withdrawFirstTableThrowsErrorSecondTableIgnored() {
        createQueryWithResults();
        doThrow(new ConcurrentModificationException()).when(mapper3).delete(any());
        
        // If mapper3 throws exception on save, do not call mapper2, and do propagate exception
        try {
            userConsentDao.withdrawConsent("AAA", STUDY_IDENTIFIER);
            fail("Should have thrown exception");
        } catch(ConcurrentModificationException e) {
            verify(mapper2).load(any());
            verify(mapper3).query(any(), any());
            verify(mapper3).delete(any());
            // No mapper 2 delete, 3 failed
            verifyNoMoreInteractions(mapper3);
            verifyNoMoreInteractions(mapper2);
        }
    }
    
    @Test
    public void withdrawSecondTableThrowsErrorFirstUndone() {
        createQueryWithResults();
        doThrow(new ConcurrentModificationException()).when(mapper2).delete(any());
        
        // If mapper2 throws exception on delete, resave to mapper3, then propagate exception 
        try {
            userConsentDao.withdrawConsent("AAA", STUDY_IDENTIFIER);
            fail("Should have thrown exception");
        } catch(ConcurrentModificationException e) {
            ArgumentCaptor<DynamoUserConsent3> argCapture = ArgumentCaptor.forClass(DynamoUserConsent3.class);
            
            verify(mapper2).load(any());
            // saves successfully
            verify(mapper3).query(any(), any());
            verify(mapper3).delete(any());
            // but two throws an error, so 3 deletes
            verify(mapper2).delete(any());
            verify(mapper3).save(argCapture.capture());
            verifyNoMoreInteractions(mapper3);
            verifyNoMoreInteractions(mapper2);
            
            // the version needs to be null on this save or it'll throw a concurrent modification exception
            assertNull(argCapture.getValue().getVersion());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void deleteFirstTableThrowsErrorSecondTableIgnored() {
        createQueryWithResults();
        doThrow(new ConcurrentModificationException()).when(mapper3).batchDelete((List<DynamoUserConsent3>)any());
        
        // If mapper3 throws exception on delete, do not call mapper2, and do propagate exception
        try {
            userConsentDao.deleteConsentRecords("AAA", STUDY_IDENTIFIER);
            fail("Should have thrown exception");
        } catch(ConcurrentModificationException e) {
            verify(mapper3).query(any(), any());
            verify(mapper3).batchDelete((List<DynamoUserConsent3>)any()); // exception thrown
            verifyNoMoreInteractions(mapper3); // No compensation needed
            verifyNoMoreInteractions(mapper2);
        }
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void deleteSecondTableThrowsErrorFirstUndone() {
        createQueryWithResults();
        doThrow(new ConcurrentModificationException()).when(mapper2).delete(any());
        
        // If mapper2 throws exception on delete, resave to mapper3, then propagate exception 
        try {
            userConsentDao.deleteConsentRecords("AAA", STUDY_IDENTIFIER);
            fail("Should have thrown exception");
        } catch(ConcurrentModificationException e) {
            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            verify(mapper3).query(any(), any());
            verify(mapper3).batchDelete((List<DynamoUserConsent3>)any());
            verify(mapper2).load(any());
            verify(mapper2).delete(any()); // exception
            verify(mapper3).batchSave(captor.capture()); // compensate for failed delete
            verifyNoMoreInteractions(mapper3);
            verifyNoMoreInteractions(mapper2);
            
            // We want to see the #3 consent has no version
            List<DynamoUserConsent3> consent = (List<DynamoUserConsent3>)captor.getValue();
            assertNull(consent.get(0).getVersion());
            
        }
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void canMigrateBetweenTablesConsentExists() {
        createQueryWithResults();
        // There is no #3 record, so migration should occur
        when(mapper3.query(any(), any())).thenReturn(mock(PaginatedQueryList.class));
        
        boolean result = userConsentDao.migrateConsent("AAA", STUDY_IDENTIFIER);
        
        ArgumentCaptor<DynamoUserConsent3> captor3 = ArgumentCaptor.forClass(DynamoUserConsent3.class);
        
        assertTrue(result);
        verify(mapper2).load(any());
        verify(mapper3).query(any(), any());
        verify(mapper3).save(captor3.capture());
        verifyNoMoreInteractions(mapper3);
        verifyNoMoreInteractions(mapper2);
        
        assertTrue(captor3.getValue().getSignedOn() > 0);
        assertEquals("AAA", captor3.getValue().getHealthCode());
        assertEquals("test-study", captor3.getValue().getStudyIdentifier());
        assertTrue(captor3.getValue().getConsentCreatedOn() > 0);
        assertNull(captor3.getValue().getVersion());
    }
    
    @Test
    public void doNotMigrateBetweenTablesWhenConsentDoesntExist() {
        createQueryWithResults();
        
        boolean result = userConsentDao.migrateConsent("AAA", STUDY_IDENTIFIER);
        
        assertFalse(result);
        verify(mapper2).load(any());
        verify(mapper3).query(any(), any());
        verifyNoMoreInteractions(mapper3);
        verifyNoMoreInteractions(mapper2);
    }

}
