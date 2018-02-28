package org.sagebionetworks.bridge.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

public class ParticipantOptionTest {

    @Test
    public void canDeserialize() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("['group1','group2']"));
        String result = ParticipantOption.DATA_GROUPS.deserialize(node);
        assertEquals("group1,group2", result);
        
        node = BridgeObjectMapper.get().readTree(TestUtils.createJson("true"));
        result = ParticipantOption.EMAIL_NOTIFICATIONS.deserialize(node);
        assertEquals("true", result);
        
        node = BridgeObjectMapper.get().readTree(TestUtils.createJson("'foo'"));
        result = ParticipantOption.EXTERNAL_IDENTIFIER.deserialize(node);
        assertEquals("foo", result);
        
        node = BridgeObjectMapper.get().readTree(TestUtils.createJson("['ja','en']"));
        result = ParticipantOption.LANGUAGES.deserialize(node);
        assertEquals("ja,en", result);
        
        node = BridgeObjectMapper.get().readTree(TestUtils.createJson("'3'"));
        result = ParticipantOption.TIME_ZONE.deserialize(node);
        assertEquals("+03:00", result);
        
        node = BridgeObjectMapper.get().readTree(TestUtils.createJson("'+0:00'"));
        result = ParticipantOption.TIME_ZONE.deserialize(node);
        assertEquals("+00:00", result);
        
        node = BridgeObjectMapper.get().readTree(TestUtils.createJson("'sponsors_and_partners'"));
        result = ParticipantOption.SHARING_SCOPE.deserialize(node);
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS.name(), result);
    }
    
    @Test
    public void canRetrieveFromStudyParticipant() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(Sets.newHashSet("group1","group2"))
                .withNotifyByEmail(true)
                .withExternalId("testExternalID")
                .withLanguages(TestUtils.newLinkedHashSet("en","de"))
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withTimeZone(DateTimeZone.forOffsetHours(-7))
                .build();
        
        String result = ParticipantOption.DATA_GROUPS.fromParticipant(participant);
        assertTrue(result.contains("group1"));
        assertTrue(result.contains("group2"));
        
        result = ParticipantOption.EMAIL_NOTIFICATIONS.fromParticipant(participant);
        assertEquals("true", result);
        
        result = ParticipantOption.EXTERNAL_IDENTIFIER.fromParticipant(participant);
        assertEquals("testExternalID", result);
        
        result = ParticipantOption.LANGUAGES.fromParticipant(participant);
        assertEquals("en,de", result);
        
        result = ParticipantOption.SHARING_SCOPE.fromParticipant(participant);
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS.name(), result);
        
        result = ParticipantOption.TIME_ZONE.fromParticipant(participant);
        assertEquals("-07:00", result);
    }
    
    @Test
    public void canRetrieveUTCFromParticipant() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withTimeZone(DateTimeZone.forOffsetHours(0))
                .build();
        
        assertEquals("UTC", participant.getTimeZone().toString());
        assertEquals("+00:00", ParticipantOption.TIME_ZONE.fromParticipant(participant));
    }
    
    @Test
    public void canRetrieveEmptyValuesFromAccount() {
        Account emptyAccount = new GenericAccount();
        assertNull(ParticipantOption.DATA_GROUPS.fromAccount(emptyAccount));
        assertEquals("true",ParticipantOption.EMAIL_NOTIFICATIONS.fromAccount(emptyAccount));
        assertNull(ParticipantOption.EXTERNAL_IDENTIFIER.fromAccount(emptyAccount));
        assertNull(ParticipantOption.LANGUAGES.fromAccount(emptyAccount));
        assertNull(ParticipantOption.SHARING_SCOPE.fromAccount(emptyAccount));
        assertNull(ParticipantOption.TIME_ZONE.fromAccount(emptyAccount));
    }
    
    @Test
    public void canRetrieveFromAccount() {
        GenericAccount account = new GenericAccount();
        account.setDataGroups(Sets.newHashSet("group1","group2"));
        account.setNotifyByEmail(false);
        account.setExternalId("testExternalID");
        account.setLanguages(TestUtils.newLinkedHashSet("en","de"));
        account.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        account.setTimeZone(DateTimeZone.forOffsetHours(-7));
        
        String result = ParticipantOption.DATA_GROUPS.fromAccount(account);
        assertTrue(result.contains("group1"));
        assertTrue(result.contains("group2"));
        
        result = ParticipantOption.EMAIL_NOTIFICATIONS.fromAccount(account);
        assertEquals("false", result);
        
        result = ParticipantOption.EXTERNAL_IDENTIFIER.fromAccount(account);
        assertEquals("testExternalID", result);
        
        result = ParticipantOption.LANGUAGES.fromAccount(account);
        assertEquals("en,de", result);
        
        result = ParticipantOption.SHARING_SCOPE.fromAccount(account);
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS.name(), result);
        
        result = ParticipantOption.TIME_ZONE.fromAccount(account);
        assertEquals("-07:00", result);
    }
    
    @Test
    public void canRetrieveUTCFromAccount() {
        Account account = new GenericAccount();
        account.setTimeZone(DateTimeZone.forOffsetHours(0));
        
        assertEquals("UTC", account.getTimeZone().toString());
        assertEquals("+00:00", ParticipantOption.TIME_ZONE.fromAccount(account));
    }
    
    @Test
    public void canRetrieveEmptyValuesFromStudyParticipant() {
        StudyParticipant emptyParticipant = new StudyParticipant.Builder().build();
        assertNull(ParticipantOption.DATA_GROUPS.fromParticipant(emptyParticipant));
        assertEquals("true",ParticipantOption.EMAIL_NOTIFICATIONS.fromParticipant(emptyParticipant));
        assertNull(ParticipantOption.EXTERNAL_IDENTIFIER.fromParticipant(emptyParticipant));
        assertNull(ParticipantOption.LANGUAGES.fromParticipant(emptyParticipant));
        assertNull(ParticipantOption.SHARING_SCOPE.fromParticipant(emptyParticipant));
        assertNull(ParticipantOption.TIME_ZONE.fromParticipant(emptyParticipant));
    }    
    
    @Test
    public void cannotBeNull() {
        for (ParticipantOption option : ParticipantOption.values()) {
            try {
                option.deserialize(null);
                fail("Should have thrown exception.");
            } catch(NullPointerException e) {
                
            }
        }
    }
    
    @Test(expected = BadRequestException.class)
    public void invalidDataGroupThrowsBadRequest() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("true"));
        ParticipantOption.DATA_GROUPS.deserialize(node);
    }
    
    @Test(expected = BadRequestException.class)
    public void invalidDataGroupMemberThrowsBadRequest() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("['group1',true]"));
        ParticipantOption.DATA_GROUPS.deserialize(node);
    }
    
    @Test(expected = BadRequestException.class)
    public void invalidDataGroupNullThrowsBadRequest() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("['group1',null]"));
        ParticipantOption.DATA_GROUPS.deserialize(node);
    }
    
    @Test(expected = BadRequestException.class)
    public void invalidEmailNotificationThrowsBadRequest() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("3"));
        ParticipantOption.EMAIL_NOTIFICATIONS.deserialize(node);
    }
    
    @Test(expected = BadRequestException.class)
    public void invalidExternalIdentifierThrowsBadRequest() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("3"));
        ParticipantOption.EXTERNAL_IDENTIFIER.deserialize(node);
    }
    
    @Test(expected = BadRequestException.class)
    public void invalidLanguagesThrowsBadRequest() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("3"));
        ParticipantOption.LANGUAGES.deserialize(node);
    }
    
    @Test(expected = BadRequestException.class)
    public void invalidSharingScopeEnumThrowsBadRequest() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("'fuzzy_bunny'"));
        ParticipantOption.SHARING_SCOPE.deserialize(node);
    }

    @Test(expected = BadRequestException.class)
    public void invalidSharingScopeTypeThrowsBadRequest() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("3"));
        ParticipantOption.SHARING_SCOPE.deserialize(node);
    }
    
    @Test(expected = BadRequestException.class)
    public void invalidTimeZoneThrowsBadRequest() throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(TestUtils.createJson("'as'"));
        ParticipantOption.TIME_ZONE.deserialize(node);
    }
}
