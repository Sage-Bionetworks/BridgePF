package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SignUpTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(StudyParticipant.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        Set<Roles> roles = Sets.newHashSet(Roles.ADMIN);
        Set<String> dataGroups = Sets.newHashSet("group1", "group2");
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com")
                .withPassword("password").withRoles(roles).withDataGroups(dataGroups).build();
        
        String json = BridgeObjectMapper.get().writeValueAsString(participant);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("email@email.com", node.get("email").asText());
        assertEquals("password", node.get("password").asText());
        assertEquals("StudyParticipant", node.get("type").asText());
        
        ArrayNode array = (ArrayNode)node.get("roles");
        assertEquals(1, array.size());
        assertEquals("admin", array.get(0).asText());
        
        array = (ArrayNode)node.get("dataGroups");
        assertEquals(2, array.size());
        
        Set<String> groupNames = Sets.newHashSet(array.get(0).asText(), array.get(1).asText());
        assertTrue(groupNames.contains("group1"));
        assertTrue(groupNames.contains("group2"));
        
        assertEquals(9, TestUtils.getFieldNamesSet(node).size());
        
        StudyParticipant newParticipant = BridgeObjectMapper.get().readValue(json, StudyParticipant.class); 
        assertEquals(participant, newParticipant);
    }
    
    @Test
    public void nullParametersBreakNothing() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com")
                .withPassword("password").build();
        
        assertEquals(0, participant.getRoles().size());
        assertEquals(0, participant.getDataGroups().size());
    }
    
    @Test
    public void oldJsonParsesCorrectly() throws Exception {
        // Old clients will continue to submit a username, this will be ignored.
        String json = "{\"email\":\"email@email.com\",\"username\":\"username@email.com\",\"password\":\"password\",\"roles\":[],\"dataGroups\":[],\"type\":\"SignUp\"}";
        
        StudyParticipant participant = BridgeObjectMapper.get().readValue(json, StudyParticipant.class);
        assertEquals("email@email.com", participant.getEmail());
        assertEquals("password", participant.getPassword());
    }

}
