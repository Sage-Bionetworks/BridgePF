package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ParticipantOptionsServiceTest {
    
    private static final String HEALTH_CODE = "AAA";
    
    private ParticipantOptionsService service;
    private ParticipantOptionsDao mockDao;
    
    @Before
    public void before() {
        service = new ParticipantOptionsService();
        mockDao = mock(ParticipantOptionsDao.class);
        service.setParticipantOptionsDao(mockDao);
        
        Study study = new DynamoStudy();
        study.setDataGroups(Sets.newHashSet("A","B","group1","group2","group3"));
    }

    @Test
    public void setBoolean() {
        service.setBoolean(TEST_STUDY, HEALTH_CODE, EMAIL_NOTIFICATIONS, true);

        verify(mockDao).setOption(TEST_STUDY, HEALTH_CODE, ParticipantOption.EMAIL_NOTIFICATIONS, Boolean.TRUE.toString());
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getBoolean() {
        when(mockDao.getOption(HEALTH_CODE, ParticipantOption.EMAIL_NOTIFICATIONS)).thenReturn("true");
        
        assertTrue(service.getBoolean(HEALTH_CODE, EMAIL_NOTIFICATIONS));
    }
    
    @Test
    public void getBooleanNull() {
        when(mockDao.getOption(HEALTH_CODE, ParticipantOption.EMAIL_NOTIFICATIONS)).thenReturn(null);
        
        assertFalse(service.getBoolean(HEALTH_CODE, EMAIL_NOTIFICATIONS));
    }
    
    @Test
    public void setString() {
        ExternalIdentifier externalId = new ExternalIdentifier("BBB");
        
        service.setString(TEST_STUDY, HEALTH_CODE, EXTERNAL_IDENTIFIER, externalId.getIdentifier());
        
        verify(mockDao).setOption(TEST_STUDY, HEALTH_CODE, ParticipantOption.EXTERNAL_IDENTIFIER, "BBB");
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getString() {
        when(mockDao.getOption(HEALTH_CODE, ParticipantOption.EXTERNAL_IDENTIFIER)).thenReturn("BBB");
        
        assertEquals("BBB", service.getString(HEALTH_CODE, EXTERNAL_IDENTIFIER));
    }
    
    @Test
    public void setEnum() {
        service.setEnum(TEST_STUDY, HEALTH_CODE, SHARING_SCOPE, SharingScope.SPONSORS_AND_PARTNERS);
        
        verify(mockDao).setOption(TEST_STUDY, HEALTH_CODE, ParticipantOption.SHARING_SCOPE, "SPONSORS_AND_PARTNERS");
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getEnum() {
        when(mockDao.getOption(HEALTH_CODE, ParticipantOption.SHARING_SCOPE)).thenReturn("SPONSORS_AND_PARTNERS");
        
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, service.getEnum(HEALTH_CODE, SHARING_SCOPE, SharingScope.class));
    }
    
    @Test
    public void getEnumNull() {
        when(mockDao.getOption(HEALTH_CODE, ParticipantOption.SHARING_SCOPE)).thenReturn(null);
        
        assertNull(service.getEnum(HEALTH_CODE, SHARING_SCOPE, SharingScope.class));
    }
    
    @Test
    public void setStringSet() {
        Set<String> dataGroups = Sets.newHashSet("group1", "group2", "group3");
        service.setStringSet(TEST_STUDY, HEALTH_CODE, DATA_GROUPS, dataGroups);
        
        // Order of the set when serialized is indeterminate, it's a set
        verify(mockDao).setOption(TEST_STUDY, HEALTH_CODE, ParticipantOption.DATA_GROUPS, BridgeUtils.setToCommaList(dataGroups));
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getStringSet() {
        when(mockDao.getOption(HEALTH_CODE, ParticipantOption.DATA_GROUPS)).thenReturn("group1,group2,group3");
        
        Set<String> dataGroups = Sets.newHashSet("group1", "group2", "group3");
        
        assertEquals(dataGroups, service.getStringSet(HEALTH_CODE, DATA_GROUPS));
    }
    
    @Test
    public void getStringSetNull() {
        when(mockDao.getOption(HEALTH_CODE, ParticipantOption.DATA_GROUPS)).thenReturn(null);
        
        assertEquals(Sets.newHashSet(), service.getStringSet(HEALTH_CODE, DATA_GROUPS));
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
        Map<ParticipantOption,String> map = Maps.newHashMap();
        map.put(ParticipantOption.DATA_GROUPS, "a,b,c");
        when(mockDao.getAllParticipantOptions(HEALTH_CODE)).thenReturn(map);
        
        Map<ParticipantOption,String> result = service.getAllParticipantOptions(HEALTH_CODE);
        assertEquals(map, result);
        
        verify(mockDao).getAllParticipantOptions(HEALTH_CODE);
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getOptionForAllStudyParticipants() {
        OptionLookup lookup = new OptionLookup(null);
        when(mockDao.getOptionForAllStudyParticipants(TEST_STUDY, ParticipantOption.EMAIL_NOTIFICATIONS)).thenReturn(lookup);
        
        OptionLookup result = service.getOptionForAllStudyParticipants(TEST_STUDY, ParticipantOption.EMAIL_NOTIFICATIONS);
        assertEquals(lookup, result);
        
        verify(mockDao).getOptionForAllStudyParticipants(TEST_STUDY, ParticipantOption.EMAIL_NOTIFICATIONS);
        verifyNoMoreInteractions(mockDao);
    }
    
    
}
