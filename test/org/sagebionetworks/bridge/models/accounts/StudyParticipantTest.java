package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class StudyParticipantTest {

    private static final String STORMPATH_ID = "6278uk74xoQkXkrbh9vJnh";
    private static final DateTime CREATED_ON = DateTime.now();
    private static final DateTime CREATED_ON_UTC = CREATED_ON.withZone(DateTimeZone.UTC);
    private static final Set<Roles> ROLES = Sets.newHashSet(Roles.ADMIN, Roles.WORKER);
    private static final LinkedHashSet<String> LANGS = TestUtils.newLinkedHashSet("en","fr");
    private static final Set<String> DATA_GROUPS = Sets.newHashSet("group1","group2");
    private static final Map<String,String> ATTRIBUTES = ImmutableMap.<String,String>builder()
            .put("A", "B")
            .put("C", "D").build();
    
    @Test
    public void hashEquals() {
        EqualsVerifier.forClass(StudyParticipant.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        StudyParticipant.Builder builder = makeParticipant();
        
        Map<String,List<UserConsentHistory>> historiesMap = Maps.newHashMap();
        
        List<UserConsentHistory> histories = Lists.newArrayList();
        UserConsentHistory history = new UserConsentHistory.Builder()
                .withBirthdate("2002-02-02")
                .withConsentCreatedOn(1000000L)
                .withSignedOn(2000000L)
                .withName("Test User")
                .withSubpopulationGuid(SubpopulationGuid.create("AAA"))
                .withWithdrewOn(3000000L).build();
        histories.add(history);
        historiesMap.put("AAA", histories);
        builder.withConsentHistories(historiesMap);
        
        StudyParticipant participant = builder.build();

        JsonNode node = BridgeObjectMapper.get().valueToTree(participant);
        
        assertEquals("firstName", node.get("firstName").asText());
        assertEquals("lastName", node.get("lastName").asText());
        assertEquals("email@email.com", node.get("email").asText());
        assertEquals("externalId", node.get("externalId").asText());
        assertEquals("newUserPassword", node.get("password").asText());
        assertEquals("sponsors_and_partners", node.get("sharingScope").asText());
        assertTrue(node.get("notifyByEmail").asBoolean());
        assertNull(node.get("healthCode"));
        assertNotNull(node.get("encryptedHealthCode"));
        assertEquals("enabled", node.get("status").asText());
        assertEquals(CREATED_ON_UTC.toString(), node.get("createdOn").asText());
        assertEquals(STORMPATH_ID, node.get("id").asText());
        assertEquals("StudyParticipant", node.get("type").asText());

        Set<String> roleNames = Sets.newHashSet(
                Roles.ADMIN.name().toLowerCase(), Roles.WORKER.name().toLowerCase());
        ArrayNode rolesArray = (ArrayNode)node.get("roles");
        assertTrue(roleNames.contains(rolesArray.get(0).asText()));
        assertTrue(roleNames.contains(rolesArray.get(1).asText()));
        
        // This array the order is significant, it serializes LinkedHashSet
        ArrayNode langsArray = (ArrayNode)node.get("languages"); 
        assertEquals("en", langsArray.get(0).asText());
        assertEquals("fr", langsArray.get(1).asText());
        
        ArrayNode dataGroupsArray = (ArrayNode)node.get("dataGroups");
        assertTrue(DATA_GROUPS.contains(dataGroupsArray.get(0).asText()));
        assertTrue(DATA_GROUPS.contains(dataGroupsArray.get(1).asText()));

        assertEquals("B", node.get("attributes").get("A").asText());
        assertEquals("D", node.get("attributes").get("C").asText());
        assertEquals(17, node.size());
        
        StudyParticipant deserParticipant = BridgeObjectMapper.get().readValue(node.toString(), StudyParticipant.class);
        assertEquals("firstName", deserParticipant.getFirstName());
        assertEquals("lastName", deserParticipant.getLastName());
        assertEquals("email@email.com", deserParticipant.getEmail());
        assertEquals("externalId", deserParticipant.getExternalId());
        assertEquals("newUserPassword", deserParticipant.getPassword());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, deserParticipant.getSharingScope());
        assertTrue(deserParticipant.isNotifyByEmail());
        assertEquals(DATA_GROUPS, deserParticipant.getDataGroups());
        assertEquals("healthCode", deserParticipant.getHealthCode());
        assertEquals(ATTRIBUTES, deserParticipant.getAttributes());
        assertEquals(CREATED_ON_UTC, deserParticipant.getCreatedOn());
        assertEquals(AccountStatus.ENABLED, deserParticipant.getStatus());
        assertEquals(STORMPATH_ID, deserParticipant.getId());
        
        UserConsentHistory deserHistory = deserParticipant.getConsentHistories().get("AAA").get(0);
        assertEquals("2002-02-02", deserHistory.getBirthdate());
        assertEquals(1000000L, deserHistory.getConsentCreatedOn());
        assertEquals(2000000L, deserHistory.getSignedOn());
        assertEquals("Test User", deserHistory.getName());
        assertEquals("AAA", deserHistory.getSubpopulationGuid());
        assertEquals(new Long(3000000L), deserHistory.getWithdrewOn());
    }

    @Test
    public void canCopy() {
        StudyParticipant participant = makeParticipant().build();
        StudyParticipant copy = new StudyParticipant.Builder().copyOf(participant).build();
        
        assertEquals("firstName", copy.getFirstName());
        assertEquals("lastName", copy.getLastName());
        assertEquals("email@email.com", copy.getEmail());
        assertEquals("externalId", copy.getExternalId());
        assertEquals("newUserPassword", copy.getPassword());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, copy.getSharingScope());
        assertTrue(copy.isNotifyByEmail());
        assertEquals(DATA_GROUPS, copy.getDataGroups());
        assertEquals("healthCode", copy.getHealthCode());
        assertEquals(ATTRIBUTES, copy.getAttributes());
        assertEquals(CREATED_ON, copy.getCreatedOn());
        assertEquals(AccountStatus.ENABLED, copy.getStatus());
        assertEquals(STORMPATH_ID, copy.getId());
        
        // And they are equal in the Java sense
        assertEquals(copy, participant);
    }
    
    @Test
    public void testNullResiliency() {
        // We don't remove nulls from the collections, at least not when reading them.
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(null)
                .withAttributes(null)
                .withRoles(null)
                .withLanguages(null).build();
        
        assertTrue(participant.getDataGroups().isEmpty());
        assertTrue(participant.getAttributes().isEmpty());
        assertTrue(participant.getRoles().isEmpty());
        assertTrue(participant.getLanguages().isEmpty());
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

    private StudyParticipant.Builder makeParticipant() {
        StudyParticipant.Builder builder = new StudyParticipant.Builder()
                .withFirstName("firstName")
                .withLastName("lastName")
                .withEmail("email@email.com")
                .withExternalId("externalId")
                .withPassword("newUserPassword")
                .withSharingScope(SharingScope.SPONSORS_AND_PARTNERS)
                .withNotifyByEmail(true)
                .withDataGroups(DATA_GROUPS)
                .withHealthCode("healthCode")
                .withAttributes(ATTRIBUTES)
                .withRoles(ROLES)
                .withLanguages(LANGS)
                .withCreatedOn(CREATED_ON)
                .withId(STORMPATH_ID)
                .withStatus(AccountStatus.ENABLED);
        return builder;
    }
}
