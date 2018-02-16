package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;
import static org.sagebionetworks.bridge.dao.ParticipantOption.TIME_ZONE;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantOptionsServiceTest {
    
    private static final String HEALTH_CODE = "AAA";
    
    private ParticipantOptionsService service;
    
    @Mock
    private AccountDao mockAccountDao;
    
    @Captor
    private ArgumentCaptor<Account> accountCaptor;
    
    private GenericAccount account;
    
    @Before
    public void before() {
        service = new ParticipantOptionsService();
        service.setAccountDao(mockAccountDao);

        account = new GenericAccount();
        
        when(mockAccountDao.getAccount(any())).thenReturn(account);
        
        Study study = new DynamoStudy();
        study.setDataGroups(Sets.newHashSet("A","B","group1","group2","group3"));
    }

    @Test
    public void setBoolean() {
        service.setBoolean(TEST_STUDY, HEALTH_CODE, EMAIL_NOTIFICATIONS, true);
        
        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(false));
        
        Account captured = accountCaptor.getValue();
        assertEquals(true, captured.getNotifyByEmail());
    }
    
    @Test
    public void getBoolean() {
        account.setNotifyByEmail(Boolean.TRUE);
        
        assertTrue(service.getOptions(TestConstants.TEST_STUDY, HEALTH_CODE).getBoolean(EMAIL_NOTIFICATIONS));
    }
    
    @Test
    public void getBooleanNull() {
        assertTrue(service.getOptions(TestConstants.TEST_STUDY, HEALTH_CODE).getBoolean(EMAIL_NOTIFICATIONS));
    }
    
    @Test
    public void setString() {
        ExternalIdentifier externalId = ExternalIdentifier.create(TEST_STUDY, "BBB");
        
        service.setString(TEST_STUDY, HEALTH_CODE, EXTERNAL_IDENTIFIER, externalId.getIdentifier());
        
        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(false));
        
        Account captured = accountCaptor.getValue();
        assertEquals(externalId.getIdentifier(), captured.getExternalId());
    }
    
    @Test
    public void getString() {
        account.setExternalId("BBB");
        
        assertEquals("BBB", service.getOptions(TestConstants.TEST_STUDY, HEALTH_CODE).getString(EXTERNAL_IDENTIFIER));
    }
    
    @Test
    public void setEnum() {
        service.setEnum(TEST_STUDY, HEALTH_CODE, SHARING_SCOPE, SharingScope.SPONSORS_AND_PARTNERS);
        
        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(false));
        
        Account captured = accountCaptor.getValue();
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, captured.getSharingScope());
    }
    
    @Test
    public void setTimeZone() {
        DateTimeZone zone = DateTimeZone.forOffsetHours(-8);
        
        service.setDateTimeZone(TEST_STUDY, HEALTH_CODE, TIME_ZONE, zone);
        
        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(false));
        
        Account captured = accountCaptor.getValue();
        assertEquals(zone, captured.getTimeZone());
    }
    
    @Test
    public void setTimeZoneUTC() {
        service.setDateTimeZone(TEST_STUDY, HEALTH_CODE, TIME_ZONE, DateTimeZone.UTC);
        
        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(false));
        
        Account captured = accountCaptor.getValue();
        assertEquals(DateTimeZone.UTC, captured.getTimeZone());
    }
    
    public void setAllOptions() {
        Map<ParticipantOption,String> options = Maps.newHashMap();
        options.put(TIME_ZONE, "-08:00");
        options.put(SHARING_SCOPE, "sponsors_and_partners");
        options.put(EMAIL_NOTIFICATIONS, "true");
        options.put(EXTERNAL_IDENTIFIER, "external-id");
        options.put(DATA_GROUPS, "group1,group2");
        options.put(LANGUAGES, "fr,en");
        
        service.setAllOptions(TEST_STUDY, HEALTH_CODE, options);
        
        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(false));
        
        Account captured = accountCaptor.getValue();
        assertEquals(DateTimeZone.forOffsetHours(-8), captured.getTimeZone());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, captured.getSharingScope());
        assertEquals(Boolean.TRUE, captured.getNotifyByEmail());
        assertEquals("external-id", captured.getExternalId());
        assertEquals(Sets.newHashSet("group1","group2"), captured.getDataGroups());
        assertEquals(TestUtils.newLinkedHashSet("fr", "en"), captured.getLanguages());
    }
    
    @Test
    public void getEnum() {
        account.setSharingScope(SharingScope.SPONSORS_AND_PARTNERS);

        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, service.getOptions(TestConstants.TEST_STUDY, HEALTH_CODE).getEnum(SHARING_SCOPE, SharingScope.class));
    }
    
    @Test
    public void getEnumNull() {
        assertEquals(SharingScope.NO_SHARING, service.getOptions(TestConstants.TEST_STUDY, HEALTH_CODE).getEnum(SHARING_SCOPE, SharingScope.class));
    }
    
    @Test
    public void setStringSet() {
        Set<String> dataGroups = Sets.newHashSet("group1", "group2", "group3");

        service.setStringSet(TEST_STUDY, HEALTH_CODE, DATA_GROUPS, dataGroups);
        
        // Order of the set when serialized is indeterminate, it's a set
        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(false));
        
        Account captured = accountCaptor.getValue();
        assertEquals(dataGroups, captured.getDataGroups());
    }
    
    @Test
    public void getStringSet() {
        Set<String> dataGroups = Sets.newHashSet("group1","group2","group3");
        account.setDataGroups(dataGroups);
        
        assertEquals(dataGroups, service.getOptions(TestConstants.TEST_STUDY, HEALTH_CODE).getStringSet(DATA_GROUPS));
    }
    
    @Test
    public void getStringSetNull() {
        account.setDataGroups(null);
        
        assertEquals(Sets.newHashSet(), service.getOptions(TestConstants.TEST_STUDY, HEALTH_CODE).getStringSet(DATA_GROUPS));
    }
    
    @
    Test
    public void getTimeZone() {
        DateTimeZone zone = DateTimeZone.forOffsetHours(-8);
        account.setTimeZone(zone);
        
        assertEquals(zone, service.getOptions(TestConstants.TEST_STUDY, HEALTH_CODE).getTimeZone(TIME_ZONE));
    }

    @Test
    public void setLinkedHashSet() {
        LinkedHashSet<String> langs = TestUtils.newLinkedHashSet("fr","en","kl");
        
        service.setOrderedStringSet(TEST_STUDY, HEALTH_CODE, LANGUAGES, langs);
        
        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(false));
        
        Account captured = accountCaptor.getValue();
        assertEquals(langs, captured.getLanguages());
    }
}
