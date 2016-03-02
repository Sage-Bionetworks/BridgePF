package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.accounts.AllParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
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

        verify(mockDao).setOption(TEST_STUDY, HEALTH_CODE, EMAIL_NOTIFICATIONS, Boolean.TRUE.toString());
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getBoolean() {
        when(mockDao.getOptions(HEALTH_CODE)).thenReturn(new ParticipantOptionsLookup(map(EMAIL_NOTIFICATIONS, "true")));
        
        assertTrue(service.getOptions(HEALTH_CODE).getBoolean(EMAIL_NOTIFICATIONS));
    }
    
    @Test
    public void getBooleanNull() {
        when(mockDao.getOptions(HEALTH_CODE)).thenReturn(new ParticipantOptionsLookup(map(EMAIL_NOTIFICATIONS, null)));
        
        assertTrue(service.getOptions(HEALTH_CODE).getBoolean(EMAIL_NOTIFICATIONS));
    }
    
    @Test
    public void setString() {
        ExternalIdentifier externalId = new ExternalIdentifier("BBB");
        
        service.setString(TEST_STUDY, HEALTH_CODE, EXTERNAL_IDENTIFIER, externalId.getIdentifier());
        
        verify(mockDao).setOption(TEST_STUDY, HEALTH_CODE, EXTERNAL_IDENTIFIER, "BBB");
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getString() {
        when(mockDao.getOptions(HEALTH_CODE)).thenReturn(new ParticipantOptionsLookup(map(EXTERNAL_IDENTIFIER, "BBB")));
        
        assertEquals("BBB", service.getOptions(HEALTH_CODE).getString(EXTERNAL_IDENTIFIER));
    }
    
    @Test
    public void setEnum() {
        service.setEnum(TEST_STUDY, HEALTH_CODE, SHARING_SCOPE, SharingScope.SPONSORS_AND_PARTNERS);
        
        verify(mockDao).setOption(TEST_STUDY, HEALTH_CODE, SHARING_SCOPE, "SPONSORS_AND_PARTNERS");
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getEnum() {
        when(mockDao.getOptions(HEALTH_CODE))
                .thenReturn(new ParticipantOptionsLookup(map(SHARING_SCOPE, "SPONSORS_AND_PARTNERS")));
        
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, service.getOptions(HEALTH_CODE).getEnum(SHARING_SCOPE, SharingScope.class));
    }
    
    @Test
    public void getEnumNull() {
        when(mockDao.getOptions(HEALTH_CODE)).thenReturn(new ParticipantOptionsLookup(map(SHARING_SCOPE, null)));
        
        assertEquals(SharingScope.NO_SHARING, service.getOptions(HEALTH_CODE).getEnum(SHARING_SCOPE, SharingScope.class));
    }
    
    @Test
    public void setStringSet() {
        Set<String> dataGroups = Sets.newHashSet("group1", "group2", "group3");

        service.setStringSet(TEST_STUDY, HEALTH_CODE, DATA_GROUPS, dataGroups);
        
        // Order of the set when serialized is indeterminate, it's a set
        verify(mockDao).setOption(TEST_STUDY, HEALTH_CODE, DATA_GROUPS, BridgeUtils.setToCommaList(dataGroups));
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getStringSet() {
        when(mockDao.getOptions(HEALTH_CODE)).thenReturn(
                new ParticipantOptionsLookup(map(DATA_GROUPS, "group1,group2,group3")));
        
        Set<String> dataGroups = Sets.newHashSet("group1", "group2", "group3");
        
        assertEquals(dataGroups, service.getOptions(HEALTH_CODE).getStringSet(DATA_GROUPS));
    }
    
    @Test
    public void getStringSetNull() {
        when(mockDao.getOptions(HEALTH_CODE)).thenReturn(
                new ParticipantOptionsLookup(map(DATA_GROUPS, null)));
        
        assertEquals(Sets.newHashSet(), service.getOptions(HEALTH_CODE).getStringSet(DATA_GROUPS));
    }

    @Test
    public void deleteAllParticipantOptions() {
        service.deleteAllParticipantOptions(HEALTH_CODE);
        
        verify(mockDao).deleteAllOptions(HEALTH_CODE);
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void deleteOption() {
        service.deleteOption(HEALTH_CODE, DATA_GROUPS);
        
        verify(mockDao).deleteOption(HEALTH_CODE, DATA_GROUPS);
        verifyNoMoreInteractions(mockDao);
    }

    @Test
    public void getAllParticipantOptions() {
        Map<String,String> map = Maps.newHashMap();
        map.put(DATA_GROUPS.name(), "a,b,c");
        
        ParticipantOptionsLookup lookup = new ParticipantOptionsLookup(map);
        
        when(mockDao.getOptions(HEALTH_CODE)).thenReturn(lookup);
        
        ParticipantOptionsLookup result = service.getOptions(HEALTH_CODE);
        assertEquals(lookup.getStringSet(DATA_GROUPS), result.getStringSet(DATA_GROUPS));
        
        verify(mockDao).getOptions(HEALTH_CODE);
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getOptionForAllStudyParticipants() {
        AllParticipantOptionsLookup allLookup = new AllParticipantOptionsLookup();
        when(mockDao.getOptionsForAllParticipants(TEST_STUDY)).thenReturn(allLookup);
        
        AllParticipantOptionsLookup result = service.getOptionsForAllParticipants(TEST_STUDY);
        assertEquals(allLookup, result);
        
        verify(mockDao).getOptionsForAllParticipants(TEST_STUDY);
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void getAllOptionsForAllStudyParticipants() {
        AllParticipantOptionsLookup allLookup = new AllParticipantOptionsLookup();
        when(mockDao.getOptionsForAllParticipants(TEST_STUDY)).thenReturn(allLookup);
        
        AllParticipantOptionsLookup result = service.getOptionsForAllParticipants(TEST_STUDY);
        assertEquals(allLookup, result);
        
        verify(mockDao).getOptionsForAllParticipants(TEST_STUDY);
        verifyNoMoreInteractions(mockDao);
    }
    
    @Test
    public void canSetLinkedHashSet() {
        when(mockDao.getOptions(HEALTH_CODE)).thenReturn(new ParticipantOptionsLookup(map(LANGUAGES, "en,fr")));
        
        LinkedHashSet<String> langs = service.getOptions(HEALTH_CODE).getOrderedStringSet(LANGUAGES);
        Iterator<String> i = langs.iterator();
        assertEquals("en", i.next());
        assertEquals("fr", i.next());
        
        langs = TestUtils.newLinkedHashSet("fr","en","kl");
        
        service.setOrderedStringSet(TEST_STUDY, HEALTH_CODE, LANGUAGES, langs);
        
        verify(mockDao).setOption(TEST_STUDY, HEALTH_CODE, LANGUAGES, "fr,en,kl");
    }
    
    private Map<String,String> map(ParticipantOption option, String value) {
        Map<String,String> map = Maps.newHashMap();
        map.put(option.name(), value);
        return map;
    }
    
}
