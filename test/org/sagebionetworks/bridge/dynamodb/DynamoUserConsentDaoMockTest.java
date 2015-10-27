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
import java.util.stream.Stream;

import org.apache.http.HttpStatus;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;

public class DynamoUserConsentDaoMockTest {

    private static final String HEALTH_CODE = "AAA";
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
        
        mockMapperResponses(null, null);
        
        userConsentDao = new DynamoUserConsentDao();
        userConsentDao.setDdbMapper2(mapper2);
        userConsentDao.setDdbMapper3(mapper3);
    }
    
    @SuppressWarnings("unchecked")
    private void mockMapperResponses(DynamoUserConsent2 consent2, DynamoUserConsent3 consent3) {
        if (consent2 != null) {
            doReturn(consent2).when(mapper2).load(any());    
        }
        PaginatedQueryList<DynamoUserConsent3> pqList = mock(PaginatedQueryList.class);
        if (consent3 == null) {
            when(pqList.isEmpty()).thenReturn(true);
        } else {
            when(pqList.isEmpty()).thenReturn(false);
            when(pqList.get(0)).thenReturn(consent3);
            when(pqList.stream()).thenReturn(Stream.of(consent3));
        }
        doReturn(pqList).when(mapper3).query(any(), any());
    }
    
    private void mockMapperResults() {
        DynamoUserConsent2 existingConsent2 = new DynamoUserConsent2(HEALTH_CODE, STUDY_IDENTIFIER.getIdentifier());
        existingConsent2.setConsentCreatedOn(studyConsent.getCreatedOn());
        existingConsent2.setSignedOn(DateTime.now().getMillis());

        DynamoUserConsent3 existingConsent3 = new DynamoUserConsent3(HEALTH_CODE, STUDY_IDENTIFIER.getIdentifier());
        existingConsent3.setConsentCreatedOn(studyConsent.getCreatedOn());
        existingConsent3.setSignedOn(DateTime.now().getMillis());
        
        mockMapperResponses(existingConsent2, existingConsent3);
    }
    
    @Test
    public void giveConsent() {
        userConsentDao.giveConsent(HEALTH_CODE, studyConsent, DateUtils.getCurrentMillisFromEpoch());
        
        ArgumentCaptor<DynamoUserConsent3> argCaptor3 = ArgumentCaptor.forClass(DynamoUserConsent3.class);
        
        verify(mapper3).query(any(), any()); // queries and saves, doesn't speak to table2
        verify(mapper2).load(any());
        verify(mapper3).save(argCaptor3.capture());
        verifyNoMoreInteractions(mapper2);
        verifyNoMoreInteractions(mapper3);
        
        DynamoUserConsent3 cons3 = argCaptor3.getValue();
        
        assertTrue(cons3.getConsentCreatedOn() > 0);
        assertTrue(cons3.getSignedOn() > 0);
        assertEquals(HEALTH_CODE, cons3.getHealthCode());
        assertEquals(HEALTH_CODE+":"+studyConsent.getStudyKey(), cons3.getHealthCodeStudy());
        assertEquals(studyConsent.getStudyKey(), cons3.getStudyIdentifier());
    }
    
    @Test
    public void giveConsentWhenConsentExists() {
        mockMapperResults();
        try {
            userConsentDao.giveConsent(HEALTH_CODE, studyConsent, DateUtils.getCurrentMillisFromEpoch());
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            assertEquals("Consent already exists.", e.getMessage());
        }
        verify(mapper3).query(any(), any()); // queried then threw exception, didn't touch table2
        verifyNoMoreInteractions(mapper2);
        verifyNoMoreInteractions(mapper3);
    }
    
    @Test
    public void giveConsentWhenOnly3Exists() {
        mockMapperResults();
        when(mapper2.load(any())).thenReturn(null);
        
        try {
            userConsentDao.giveConsent(HEALTH_CODE, studyConsent, DateUtils.getCurrentMillisFromEpoch());    
        } catch(BridgeServiceException e) {
            assertEquals("Consent already exists.", e.getMessage());
        }
    }
    
    @Test
    public void giveConsentWhenOnly2Exists() {
        DynamoUserConsent2 existingConsent2 = new DynamoUserConsent2(HEALTH_CODE, STUDY_IDENTIFIER.getIdentifier());
        existingConsent2.setConsentCreatedOn(studyConsent.getCreatedOn());
        existingConsent2.setSignedOn(DateTime.now().getMillis());
        
        when(mapper2.load(any())).thenReturn(existingConsent2);
        
        try {
            userConsentDao.giveConsent(HEALTH_CODE, studyConsent, DateUtils.getCurrentMillisFromEpoch());    
        } catch(BridgeServiceException e) {
            assertEquals("Consent already exists.", e.getMessage());
            assertEquals(HttpStatus.SC_CONFLICT, e.getStatusCode());
        }
    }
    
    @Test
    public void hasConsentedWhenTable3Exists() {
        mockMapperResults();
        
        boolean hasConsented = userConsentDao.hasConsented(HEALTH_CODE, STUDY_IDENTIFIER);
        
        assertTrue(hasConsented);
        verify(mapper3).query(any(), any());
        verifyNoMoreInteractions(mapper2);
        verifyNoMoreInteractions(mapper3);
    }
    
    @Test
    public void hasConsentedWhenTable2Exists() {
        DynamoUserConsent2 existingConsent2 = new DynamoUserConsent2(HEALTH_CODE, STUDY_IDENTIFIER.getIdentifier());
        existingConsent2.setConsentCreatedOn(studyConsent.getCreatedOn());
        existingConsent2.setSignedOn(DateTime.now().getMillis());

        doReturn(existingConsent2).when(mapper2).load(any());
        
        boolean hasConsented = userConsentDao.hasConsented(HEALTH_CODE, STUDY_IDENTIFIER);
        
        assertTrue(hasConsented);
        verify(mapper3).query(any(), any());
        verify(mapper2).load(any());
        verifyNoMoreInteractions(mapper2);
        verifyNoMoreInteractions(mapper3);
    }
    
    @Test
    public void hasConsentedNoConsent() {
        // Very similar to consenting when consent is in table 2, except, no consent there.
        boolean hasConsented = userConsentDao.hasConsented(HEALTH_CODE, STUDY_IDENTIFIER);
        
        assertFalse(hasConsented);
        verify(mapper3).query(any(), any());
        verify(mapper2).load(any());
        verifyNoMoreInteractions(mapper2);
        verifyNoMoreInteractions(mapper3);
    }
    
    @Test
    public void withdrawUpdatesBothTables() {
        mockMapperResults();
        
        userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        
        verify(mapper2).load(any());
        verify(mapper2).delete(any(DynamoUserConsent2.class));
        verify(mapper3).query(any(), any());
        verify(mapper3).delete(any(DynamoUserConsent3.class));
        verifyNoMoreInteractions(mapper2);
        verifyNoMoreInteractions(mapper3);
    }
    
    @Test
    public void withdrawFirstTableThrowsErrorSecondTableIgnored() {
        mockMapperResults();
        doThrow(new ConcurrentModificationException()).when(mapper2).delete(any());
        
        try {
            userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER);
            fail("Should have thrown exception");
        } catch(ConcurrentModificationException e) {
            verify(mapper2).load(any());
            verify(mapper2).delete(any()); // this fails, mapper2 not called
            verifyNoMoreInteractions(mapper2);
            verifyNoMoreInteractions(mapper3);
        }
    }
    
    @Test
    public void withdrawSecondTableThrowsException() {
        mockMapperResults();
        doThrow(new ConcurrentModificationException()).when(mapper3).delete(any());
        
        // If mapper2 throws exception on delete, resave to mapper3, then propagate exception 
        try {
            userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER);
            fail("Should have thrown exception");
        } catch(ConcurrentModificationException e) {
            verify(mapper2).load(any());
            verify(mapper2).delete(any()); // throws exception
            verify(mapper3).query(any(), any());
            verify(mapper3).delete(any());
            verifyNoMoreInteractions(mapper2);
            verifyNoMoreInteractions(mapper3);
        }
    }
    
    @Test
    public void getConsentFromTable3First() {
        mockMapperResults();
        
        UserConsent consent = userConsentDao.getUserConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertTrue(consent instanceof DynamoUserConsent3);
        assertEquals(STUDY_IDENTIFIER.getIdentifier(), consent.getStudyIdentifier());
        
        verify(mapper3).query(any(), any());
        verifyNoMoreInteractions(mapper2);
        verifyNoMoreInteractions(mapper3);
    }

    @Test
    public void getConsentFallsbackToTable2() {
        DynamoUserConsent2 existingConsent2 = new DynamoUserConsent2(HEALTH_CODE, STUDY_IDENTIFIER.getIdentifier());
        existingConsent2.setConsentCreatedOn(studyConsent.getCreatedOn());
        existingConsent2.setSignedOn(DateTime.now().getMillis());
        doReturn(existingConsent2).when(mapper2).load(any());

        UserConsent consent = userConsentDao.getUserConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertTrue(consent instanceof DynamoUserConsent2);
        assertEquals(STUDY_IDENTIFIER.getIdentifier(), consent.getStudyIdentifier());
        
        verify(mapper3).query(any(), any());
        verify(mapper2).load(any());
        verifyNoMoreInteractions(mapper2);
        verifyNoMoreInteractions(mapper3);
    }
    
    @Test
    public void deleteFirstTableThrowsErrorSecondTableIgnored() {
        mockMapperResults();
        doThrow(new ConcurrentModificationException()).when(mapper2).delete(any());
        
        // If mapper3 throws exception on delete, do not call mapper2, and do propagate exception
        try {
            userConsentDao.deleteConsentRecords(HEALTH_CODE, STUDY_IDENTIFIER);
            fail("Should have thrown exception");
        } catch(ConcurrentModificationException e) {
            verify(mapper2).load(any());
            verify(mapper2).delete(any());
            verifyNoMoreInteractions(mapper3);
            verifyNoMoreInteractions(mapper2);
        }
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void canMigrateBetweenTablesConsentExists() {
        mockMapperResults();
        // There is no #3 record, so migration should occur
        when(mapper3.query(any(), any())).thenReturn(mock(PaginatedQueryList.class));
        
        boolean result = userConsentDao.migrateConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        
        ArgumentCaptor<DynamoUserConsent3> captor3 = ArgumentCaptor.forClass(DynamoUserConsent3.class);
        
        assertTrue(result);
        verify(mapper2).load(any());
        verify(mapper3).query(any(), any());
        verify(mapper3).save(captor3.capture());
        verifyNoMoreInteractions(mapper3);
        verifyNoMoreInteractions(mapper2);
        
        assertTrue(captor3.getValue().getSignedOn() > 0);
        assertEquals(HEALTH_CODE, captor3.getValue().getHealthCode());
        assertEquals(STUDY_IDENTIFIER.getIdentifier(), captor3.getValue().getStudyIdentifier());
        assertTrue(captor3.getValue().getConsentCreatedOn() > 0);
        assertNull(captor3.getValue().getVersion());
    }
    
    @Test
    public void doNotMigrateBetweenTablesWhenConsentDoesntExist() {
        mockMapperResults();
        
        boolean result = userConsentDao.migrateConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        
        assertFalse(result);
        verify(mapper2).load(any());
        verify(mapper3).query(any(), any());
        verifyNoMoreInteractions(mapper3);
        verifyNoMoreInteractions(mapper2);
    }

}
