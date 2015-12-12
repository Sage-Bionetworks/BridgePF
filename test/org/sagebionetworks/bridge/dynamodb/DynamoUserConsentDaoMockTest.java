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
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;

public class DynamoUserConsentDaoMockTest {

    private static final long UNIX_TIMESTAMP = DateTime.now().getMillis();
    private static final String HEALTH_CODE = "AAA";
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("GUID");
    private static final StudyIdentifier STUDY_IDENTIFIER = new StudyIdentifierImpl("test-study");
    
    DynamoUserConsentDao userConsentDao;
    
    DynamoStudyConsent1 studyConsent;
    
    DynamoDBMapper mapper;
    
    @Before
    public void before() {
        studyConsent = new DynamoStudyConsent1();
        studyConsent.setActive(true);
        studyConsent.setCreatedOn(DateTime.now().getMillis());
        studyConsent.setSubpopulationGuid("test-study");
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
    
    private DynamoUserConsent3 mockMapperResponse() {
        DynamoUserConsent3 consent = new DynamoUserConsent3(HEALTH_CODE, SubpopulationGuid.create(STUDY_IDENTIFIER.getIdentifier()));
        consent.setConsentCreatedOn(studyConsent.getCreatedOn());
        consent.setSignedOn(DateTime.now().getMillis());
        
        mockMapperQueryResponse(consent);
        return consent;
    }
    
    @Test
    public void giveConsent() {
        userConsentDao.giveConsent(HEALTH_CODE, SUBPOP_GUID, studyConsent.getCreatedOn(), DateUtils.getCurrentMillisFromEpoch());
        
        ArgumentCaptor<DynamoUserConsent3> argCaptor3 = ArgumentCaptor.forClass(DynamoUserConsent3.class);
        
        verify(mapper).query(any(), any());
        verify(mapper).save(argCaptor3.capture());
        verifyNoMoreInteractions(mapper);

        DynamoUserConsent3 cons3 = argCaptor3.getValue();
        
        assertTrue(cons3.getConsentCreatedOn() > 0);
        assertTrue(cons3.getSignedOn() > 0);
        assertEquals(HEALTH_CODE, cons3.getHealthCode());
        assertEquals(HEALTH_CODE+":"+studyConsent.getSubpopulationGuid(), cons3.getHealthCodeSubpopGuid());
        assertEquals(studyConsent.getSubpopulationGuid(), cons3.getSubpopulationGuid());
    }
    
    @Test
    public void giveConsentWhenConsentExists() {
        mockMapperResponse();
        try {
            userConsentDao.giveConsent(HEALTH_CODE, SUBPOP_GUID, studyConsent.getCreatedOn(), DateUtils.getCurrentMillisFromEpoch());
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            assertEquals("UserConsent already exists.", e.getMessage());
            assertEquals(HttpStatus.SC_CONFLICT, e.getStatusCode());
        }
        verify(mapper).query(any(), any());
        verifyNoMoreInteractions(mapper);
    }
    
    @Test
    public void getUserConsent() {
        UserConsent consent = mockMapperResponse();
        
        UserConsent consent2 = userConsentDao.getActiveUserConsent(HEALTH_CODE, SUBPOP_GUID);
        assertEquals(consent, consent2);
        
        verify(mapper).query(any(), any());
        verifyNoMoreInteractions(mapper);
    }
    
    @Test
    public void hasConsentedConsentExists() {
        mockMapperResponse();
        
        boolean hasConsented = userConsentDao.hasConsented(HEALTH_CODE, SUBPOP_GUID);
        
        assertTrue(hasConsented);
        verify(mapper).query(any(), any());
        verifyNoMoreInteractions(mapper);
    }
    
    @Test
    public void hasConsentedNoConsent() {
        boolean hasConsented = userConsentDao.hasConsented(HEALTH_CODE, SUBPOP_GUID);
        
        assertFalse(hasConsented);
        verify(mapper).query(any(), any());
        verifyNoMoreInteractions(mapper);
    }
    
    @Test
    public void withdrawConsent() {
        mockMapperResponse();

        userConsentDao.withdrawConsent(HEALTH_CODE, SUBPOP_GUID, UNIX_TIMESTAMP);

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
            userConsentDao.withdrawConsent(HEALTH_CODE, SUBPOP_GUID, UNIX_TIMESTAMP);
            fail("Should have thrown exception.");
        } catch(BridgeServiceException e) {
            assertEquals("UserConsent not found.", e.getMessage());
            assertEquals(404, e.getStatusCode());
        }
        verify(mapper).query(any(), any());
        verifyNoMoreInteractions(mapper);
    }
    
    // These are basic, more extensive tests can be found in DynamoUserConsentDaoTest.
    
    @Test
    public void getActiveUserConsent() {
        mockMapperResponse();
        
        UserConsent consent = userConsentDao.getActiveUserConsent(HEALTH_CODE, SUBPOP_GUID);
        assertNotNull(consent);
        assertNull(consent.getWithdrewOn());
    }
    
    @Test
    public void getUserConsentHistory() {
        List<UserConsent> history = userConsentDao.getUserConsentHistory(HEALTH_CODE, SUBPOP_GUID);
        assertTrue(history.isEmpty());
        
        mockMapperResponse();
        history = userConsentDao.getUserConsentHistory(HEALTH_CODE, SUBPOP_GUID);
        assertEquals(1, history.size());
    }
}
