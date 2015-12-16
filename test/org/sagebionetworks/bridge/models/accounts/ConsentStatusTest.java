package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ConsentStatusTest {

    private static final ConsentStatus REQUIRED_SIGNED_CURRENT = new ConsentStatus("Name1", "foo1", true, true, true);
    private static final ConsentStatus REQUIRED_SIGNED_OBSOLETE = new ConsentStatus("Name1", "foo2", true, true, false);
    private static final ConsentStatus OPTIONAL_SIGNED_CURRENT = new ConsentStatus("Name1", "foo3", false, true, true);
    private static final ConsentStatus OPTIONAL_SIGNED_OBSOLETE = new ConsentStatus("Name1", "foo4", false, true, false);
    private static final ConsentStatus REQUIRED_UNSIGNED = new ConsentStatus("Name1", "foo5", true, false, false);
    private static final ConsentStatus OPTIONAL_UNSIGNED = new ConsentStatus("Name1", "foo6", false, false, false);

    private List<ConsentStatus> statuses;
    
    @Before
    public void before() {
        statuses = Lists.newArrayList();
    }
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(ConsentStatus.class).allFieldsShouldBeUsed().verify(); 
    }
    
    // Will be stored as JSON in the the session, via the User object, so it must serialize.
    @Test
    public void canSerialize() throws Exception {
        ConsentStatus status = new ConsentStatus.Builder().withName("Name").withGuid(SubpopulationGuid.create("GUID"))
                .withConsented(true).withRequired(true).withSignedMostRecentConsent(true).build();

        String json = BridgeObjectMapper.get().writeValueAsString(status);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("Name", node.get("name").asText());
        assertEquals("GUID", node.get("subpopulationGuid").asText());
        assertTrue(node.get("required").asBoolean());
        assertTrue(node.get("consented").asBoolean());
        assertTrue(node.get("signedMostRecentConsent").asBoolean());
        assertEquals("ConsentStatus", node.get("type").asText());
        
        ConsentStatus status2 = BridgeObjectMapper.get().readValue(json, ConsentStatus.class);
        assertEquals(status, status2);
    }

    @Test
    public void forSubpopulation() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid("test");
        
        assertNull(ConsentStatus.forSubpopulation(statuses, subpop));
        
        ConsentStatus status1 = new ConsentStatus("Name1", "foo", false, false, false);
        ConsentStatus status2 = new ConsentStatus("Name2", "test", false, false, false);
        statuses.add(status1);
        statuses.add(status2);
        
        assertEquals(status2, ConsentStatus.forSubpopulation(statuses, subpop));
    }

    @Test
    public void isUserConsented() {
        assertFalse(ConsentStatus.isUserConsented(statuses));
        
        statuses.add(REQUIRED_UNSIGNED);
        statuses.add(REQUIRED_SIGNED_CURRENT);
        statuses.add(OPTIONAL_SIGNED_CURRENT);
        statuses.add(OPTIONAL_SIGNED_OBSOLETE);
        assertFalse(ConsentStatus.isUserConsented(statuses));
        
        statuses.clear();
        statuses.add(REQUIRED_SIGNED_CURRENT);
        statuses.add(REQUIRED_UNSIGNED);
        statuses.add(OPTIONAL_UNSIGNED);
        statuses.add(OPTIONAL_SIGNED_OBSOLETE);
        assertFalse(ConsentStatus.isUserConsented(statuses));

        statuses.clear();
        statuses.add(REQUIRED_SIGNED_CURRENT);
        statuses.add(REQUIRED_SIGNED_OBSOLETE);
        statuses.add(OPTIONAL_UNSIGNED);
        statuses.add(OPTIONAL_SIGNED_OBSOLETE);
        assertTrue(ConsentStatus.isUserConsented(statuses));
    }

    @Test
    public void isConsentCurrent() {
        assertFalse(ConsentStatus.isConsentCurrent(statuses));
        
        statuses.add(REQUIRED_SIGNED_CURRENT);
        statuses.add(REQUIRED_SIGNED_OBSOLETE);
        statuses.add(OPTIONAL_UNSIGNED);
        assertFalse(ConsentStatus.isConsentCurrent(statuses));
        
        statuses.clear();
        statuses.add(REQUIRED_SIGNED_CURRENT);
        statuses.add(OPTIONAL_SIGNED_OBSOLETE); // only required consents are considered 
        assertTrue(ConsentStatus.isConsentCurrent(statuses));
        
        statuses.clear();
        statuses.add(REQUIRED_SIGNED_CURRENT);
        statuses.add(REQUIRED_SIGNED_CURRENT);
        statuses.add(OPTIONAL_UNSIGNED);
        assertTrue(ConsentStatus.isConsentCurrent(statuses));
    }
    
    @Test
    public void hasOnlyOneSignedConsent() {
        assertFalse(ConsentStatus.hasOnlyOneSignedConsent(statuses));
        
        statuses.add(REQUIRED_UNSIGNED);
        statuses.add(OPTIONAL_SIGNED_OBSOLETE);
        assertTrue(ConsentStatus.hasOnlyOneSignedConsent(statuses));
        
        statuses.clear();
        statuses.add(REQUIRED_SIGNED_CURRENT);
        statuses.add(REQUIRED_SIGNED_OBSOLETE);
        statuses.add(REQUIRED_UNSIGNED);
        assertFalse(ConsentStatus.hasOnlyOneSignedConsent(statuses));
        
        statuses.clear();
        statuses.add(REQUIRED_SIGNED_CURRENT);
        statuses.add(REQUIRED_UNSIGNED);
        statuses.add(OPTIONAL_UNSIGNED);
        assertTrue(ConsentStatus.hasOnlyOneSignedConsent(statuses));
    }
   
    @Test
    public void mapSerializesCorrectly() throws Exception {
        Map<SubpopulationGuid,ConsentStatus> map = Maps.newHashMap();
        map.put(SubpopulationGuid.create(REQUIRED_SIGNED_CURRENT.getSubpopulationGuid()), REQUIRED_SIGNED_CURRENT);
        map.put(SubpopulationGuid.create(REQUIRED_SIGNED_OBSOLETE.getSubpopulationGuid()), REQUIRED_SIGNED_OBSOLETE);
        map.put(SubpopulationGuid.create(REQUIRED_UNSIGNED.getSubpopulationGuid()), REQUIRED_UNSIGNED);

        String json = BridgeObjectMapper.get().writeValueAsString(map);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertNotNull(node.get("foo1"));
        assertNotNull(node.get("foo2"));
        assertNotNull(node.get("foo5"));
        
        JsonNode consentNode = node.get("foo5");
        ConsentStatus consent5 = BridgeObjectMapper.get().readValue(consentNode.toString(), ConsentStatus.class);
        assertEquals(REQUIRED_UNSIGNED, consent5);
    }
    
}
