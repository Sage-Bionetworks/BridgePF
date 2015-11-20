package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.DataGroups;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Sets;

public class ParticipantOptionsServiceImplTest {
    
    private static final String HEALTH_CODE = "AAA";
    
    private ParticipantOptionsServiceImpl service;
    private ParticipantOptionsDao mockDao;
    private StudyService mockStudyService;
    
    @Before
    public void before() {
        service = new ParticipantOptionsServiceImpl();
        mockDao = mock(ParticipantOptionsDao.class);
        mockStudyService = mock(StudyService.class);
        service.setParticipantOptionsDao(mockDao);
        service.setStudyService(mockStudyService);
        
        Study study = new DynamoStudy();
        study.setDataGroups(Sets.newHashSet("A","B","group1","group2","group3"));
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
    }

    @Test
    public void setEmailNotifications() {
        service.setEmailNotifications(TEST_STUDY, HEALTH_CODE, Boolean.TRUE);
        
        verify(mockDao).setOption(TEST_STUDY, HEALTH_CODE, ParticipantOption.EMAIL_NOTIFICATIONS, Boolean.TRUE.toString());
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getEmailNotifications() {
        when(mockDao.getOption(HEALTH_CODE, ParticipantOption.EMAIL_NOTIFICATIONS)).thenReturn("true");
        
        assertTrue(service.getEmailNotifications(HEALTH_CODE));
    }
    
    @Test
    public void setExternalIdentifier() {
        ExternalIdentifier externalId = new ExternalIdentifier("BBB");
        
        service.setExternalIdentifier(TEST_STUDY, HEALTH_CODE, externalId);
        
        verify(mockDao).setOption(TEST_STUDY, HEALTH_CODE, ParticipantOption.EXTERNAL_IDENTIFIER, "BBB");
        verifyNoMoreInteractions(mockDao);
    }

    @Test
    public void setExternalIdentifierValidatesNotNull() {
        try {
            ExternalIdentifier externalId = new ExternalIdentifier(null);
            service.setExternalIdentifier(TEST_STUDY, HEALTH_CODE, externalId);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("ExternalIdentifier is not valid.", e.getMessage());
        }
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void setExternalIdentifierValidatesNotBlank() {
        try {
            ExternalIdentifier externalId = new ExternalIdentifier("   ");
            service.setExternalIdentifier(TEST_STUDY, HEALTH_CODE, externalId);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals("ExternalIdentifier is not valid.", e.getMessage());
        }
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getExternalIdentifier() {
        when(mockDao.getOption(HEALTH_CODE, ParticipantOption.EXTERNAL_IDENTIFIER)).thenReturn("BBB");
        
        assertEquals("BBB", service.getExternalIdentifier(HEALTH_CODE));
    }
   
    @Test
    public void setSharingScope() {
        service.setSharingScope(TEST_STUDY, HEALTH_CODE, SharingScope.SPONSORS_AND_PARTNERS);
        
        verify(mockDao).setOption(TEST_STUDY, HEALTH_CODE, ParticipantOption.SHARING_SCOPE, "SPONSORS_AND_PARTNERS");
        verifyNoMoreInteractions(mockDao);
        
    }
    
    @Test
    public void getSharingScope() {
        when(mockDao.getOption(HEALTH_CODE, ParticipantOption.SHARING_SCOPE)).thenReturn("SPONSORS_AND_PARTNERS");
        
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, service.getSharingScope(HEALTH_CODE));
    }

    @Test
    public void setDataGroups() {
        Set<String> dataGroups = Sets.newHashSet("group1", "group2", "group3");
        service.setDataGroups(TEST_STUDY, HEALTH_CODE, new DataGroups(dataGroups));
        
        // Order of the set when serialized is indeterminate, it's a set
        verify(mockDao).setOption(TEST_STUDY, HEALTH_CODE, ParticipantOption.DATA_GROUPS, BridgeUtils.setToCommaList(dataGroups));
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void setDataGroupsValidatesBadValues() {
        try {
            // Null is simply removed in the construction of an immutable set.
            Set<String> dataGroups = Sets.newHashSet("A", null, " ", "B");
            service.setDataGroups(TEST_STUDY, HEALTH_CODE, new DataGroups(dataGroups));
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getErrors().get("dataGroups").get(0).contains("dataGroups ' ' is not one of these valid values: "));
        }
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getDataGroups() {
        when(mockDao.getOption(HEALTH_CODE, ParticipantOption.DATA_GROUPS)).thenReturn("group1,group2,group3");
        
        Set<String> dataGroups = Sets.newHashSet("group1", "group2", "group3");
        assertEquals(dataGroups, service.getDataGroups(HEALTH_CODE));
    }
    
    @Test
    public void deleteAllParticipantOptions() {
        service.deleteAllParticipantOptions(HEALTH_CODE);
        
        verify(mockDao).deleteAllParticipantOptions(HEALTH_CODE);
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void deleteOption() {
        service.deleteOption(HEALTH_CODE, ParticipantOption.DATA_GROUPS);
        
        verify(mockDao).deleteOption(HEALTH_CODE, ParticipantOption.DATA_GROUPS);
        verifyNoMoreInteractions(mockDao);
    }

    @Test
    public void getAllParticipantOptions() {
        service.getAllParticipantOptions(HEALTH_CODE);
        
        verify(mockDao).getAllParticipantOptions(HEALTH_CODE);
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getOptionForAllStudyParticipants() {
        service.getOptionForAllStudyParticipants(TEST_STUDY, ParticipantOption.EMAIL_NOTIFICATIONS);
        
        verify(mockDao).getOptionForAllStudyParticipants(TEST_STUDY, ParticipantOption.EMAIL_NOTIFICATIONS);
        verifyNoMoreInteractions(mockDao);
    }
    
    
}
