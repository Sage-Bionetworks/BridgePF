package org.sagebionetworks.bridge.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
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
                .build();
        
        String result = ParticipantOption.DATA_GROUPS.fromParticipant(participant);
        assertEquals("group1,group2", result);
        
        result = ParticipantOption.EMAIL_NOTIFICATIONS.fromParticipant(participant);
        assertEquals("true", result);
        
        result = ParticipantOption.EXTERNAL_IDENTIFIER.fromParticipant(participant);
        assertEquals("testExternalID", result);
        
        result = ParticipantOption.LANGUAGES.fromParticipant(participant);
        assertEquals("ja,en", result);
        
        result = ParticipantOption.SHARING_SCOPE.fromParticipant(participant);
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS.name(), result);
    }
    
    @Test
    public void canRetrieveEmptyValuesFromStudyParticipant() {
        StudyParticipant emptyParticipant = new StudyParticipant.Builder().build();
        assertNull(ParticipantOption.DATA_GROUPS.fromParticipant(emptyParticipant));
        assertNull(ParticipantOption.EMAIL_NOTIFICATIONS.fromParticipant(emptyParticipant));
        assertNull(ParticipantOption.EXTERNAL_IDENTIFIER.fromParticipant(emptyParticipant));
        assertNull(ParticipantOption.LANGUAGES.fromParticipant(emptyParticipant));
        assertNull(ParticipantOption.SHARING_SCOPE.fromParticipant(emptyParticipant));
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
}
