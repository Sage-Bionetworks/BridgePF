package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
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
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;

public class DynamoUserConsentDaoMockTest {

    private static final long UNIX_TIMESTAMP = DateTime.now().getMillis();
    private static final String HEALTH_CODE = "AAA";
    private static final StudyIdentifier STUDY_IDENTIFIER = new StudyIdentifierImpl("test-study");
    
    DynamoUserConsentDao userConsentDao;
    
    DynamoStudyConsent1 studyConsent;
    
    DynamoDBMapper mapper;
    
    @Before
    public void before() {
        studyConsent = new DynamoStudyConsent1();
        studyConsent.setActive(true);
        studyConsent.setCreatedOn(DateTime.now().getMillis());
        studyConsent.setStudyKey("test-study");
        studyConsent.setStoragePath("test-study.12341234123");
        
        mapper = mock(DynamoDBMapper.class);
        
        mockMapperQueryResponse(null);
        
        userConsentDao = new DynamoUserConsentDao();
        userConsentDao.setDdbMapper(mapper);
    }
    
    @SuppressWarnings("unchecked")
    private void mockMapperQueryResponse(DynamoUserConsent3 consent) {
        PaginatedQueryList<DynamoUserConsent3> pqList = mock(PaginatedQueryList.class);
        if (consent == null) {
            when(pqList.isEmpty()).thenReturn(true);
            when(pqList.stream()).thenReturn(Stream.of());
        } else {
            when(pqList.isEmpty()).thenReturn(false);
            when(pqList.get(0)).thenReturn(consent);
            when(pqList.stream()).thenReturn(Stream.of(consent));
        }
        doReturn(pqList).when(mapper).query(any(), any());
    }
    
    @SuppressWarnings("unchecked")
    private void mockMapperScanResponse(DynamoUserConsent3 consent) {
        PaginatedScanList<DynamoUserConsent3> psList = mock(PaginatedScanList.class);
        if (consent == null) {
            when(psList.isEmpty()).thenReturn(true);
            when(psList.stream()).thenReturn(Stream.of());
        } else {
            when(psList.isEmpty()).thenReturn(false);
            when(psList.get(0)).thenReturn(consent);
            when(psList.stream()).thenReturn(Stream.of(consent));
        }
        doReturn(psList).when(mapper).scan(any(), any());
    }
    
    private DynamoUserConsent3 mockMapperResponse() {
        DynamoUserConsent3 consent = new DynamoUserConsent3(HEALTH_CODE, STUDY_IDENTIFIER.getIdentifier());
        consent.setConsentCreatedOn(studyConsent.getCreatedOn());
        consent.setSignedOn(DateTime.now().getMillis());
        
        mockMapperQueryResponse(consent);
        return consent;
    }
    
    @Test
    public void giveConsent() {
        userConsentDao.giveConsent(HEALTH_CODE, studyConsent, DateUtils.getCurrentMillisFromEpoch());
        
        ArgumentCaptor<DynamoUserConsent3> argCaptor3 = ArgumentCaptor.forClass(DynamoUserConsent3.class);
        
        verify(mapper).query(any(), any());
        verify(mapper).save(argCaptor3.capture());
        verifyNoMoreInteractions(mapper);

        DynamoUserConsent3 cons3 = argCaptor3.getValue();
        
        assertTrue(cons3.getConsentCreatedOn() > 0);
        assertTrue(cons3.getSignedOn() > 0);
        assertEquals(HEALTH_CODE, cons3.getHealthCode());
        assertEquals(HEALTH_CODE+":"+studyConsent.getStudyKey(), cons3.getHealthCodeStudy());
        assertEquals(studyConsent.getStudyKey(), cons3.getStudyIdentifier());
    }
    
    @Test
    public void giveConsentWhenConsentExists() {
        mockMapperResponse();
        try {
            userConsentDao.giveConsent(HEALTH_CODE, studyConsent, DateUtils.getCurrentMillisFromEpoch());
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            assertEquals("Consent already exists.", e.getMessage());
            assertEquals(HttpStatus.SC_CONFLICT, e.getStatusCode());
        }
        verify(mapper).query(any(), any());
        verifyNoMoreInteractions(mapper);
    }
    
    @Test
    public void getUserConsent() {
        UserConsent consent = mockMapperResponse();
        
        UserConsent consent2 = userConsentDao.getActiveUserConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertEquals(consent, consent2);
        
        verify(mapper).query(any(), any());
        verifyNoMoreInteractions(mapper);
    }
    
    @Test
    public void hasConsentedConsentExists() {
        mockMapperResponse();
        
        boolean hasConsented = userConsentDao.hasConsented(HEALTH_CODE, STUDY_IDENTIFIER);
        
        assertTrue(hasConsented);
        verify(mapper).query(any(), any());
        verifyNoMoreInteractions(mapper);
    }
    
    @Test
    public void hasConsentedNoConsent() {
        boolean hasConsented = userConsentDao.hasConsented(HEALTH_CODE, STUDY_IDENTIFIER);
        
        assertFalse(hasConsented);
        verify(mapper).query(any(), any());
        verifyNoMoreInteractions(mapper);
    }
    
    @Test
    public void withdrawConsent() {
        mockMapperResponse();

        userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER, UNIX_TIMESTAMP);

        ArgumentCaptor<DynamoUserConsent3> captor = ArgumentCaptor.forClass(DynamoUserConsent3.class);
        
        verify(mapper).query(any(), any());
        verify(mapper).save(captor.capture());
        verifyNoMoreInteractions(mapper);
        
        DynamoUserConsent3 consent = captor.getValue();
        assertTrue(consent.getWithdrewOn() > 0L);
    }
    
    @Test
    public void withdrawConsentWithNoConsent() {
        try {
            userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER, UNIX_TIMESTAMP);
            fail("Should have thrown exception.");
        } catch(BridgeServiceException e) {
            assertEquals("Consent not found.", e.getMessage());
            assertEquals(404, e.getStatusCode());
        }
        verify(mapper).query(any(), any());
        verifyNoMoreInteractions(mapper);
    }
    
    // These are basic, more extensive tests can be found in DynamoUserConsentDaoTest.
    
    @Test
    public void getActiveUserConsent() {
        mockMapperResponse();
        
        UserConsent consent = userConsentDao.getActiveUserConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertNotNull(consent);
        assertNull(consent.getWithdrewOn());
    }
    
    @Test
    public void getUserConsentHistory() {
        List<UserConsent> history = userConsentDao.getUserConsentHistory(HEALTH_CODE, STUDY_IDENTIFIER);
        assertTrue(history.isEmpty());
        
        mockMapperResponse();
        history = userConsentDao.getUserConsentHistory(HEALTH_CODE, STUDY_IDENTIFIER);
        assertEquals(1, history.size());
    }

    @Test
    public void getNumberOfParticipants() {
        DynamoUserConsent3 consent = new DynamoUserConsent3(HEALTH_CODE, STUDY_IDENTIFIER.getIdentifier());
        consent.setConsentCreatedOn(studyConsent.getCreatedOn());
        consent.setSignedOn(DateTime.now().getMillis());
        
        mockMapperScanResponse(consent);
        
        long count = userConsentDao.getNumberOfParticipants(STUDY_IDENTIFIER);
        assertEquals(1L, count);
    }
}
