package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ConsentStatusTest {

    private Map<SubpopulationGuid,ConsentStatus> statuses;
    
    @Before
    public void before() {
        statuses = Maps.newHashMap();
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
        subpop.setGuidString("test");
        
        assertNull(statuses.get(subpop.getGuid()));
        
        ConsentStatus status1 = new ConsentStatus("Name1", "foo", false, false, false);
        ConsentStatus status2 = new ConsentStatus("Name2", "test", false, false, false);
        statuses.put(subpop.getGuid(), status1);
        statuses.put(subpop.getGuid(), status2);
        
        assertEquals(status2, statuses.get(subpop.getGuid()));
    }
    
    private void add(ConsentStatus status) {
        statuses.put(SubpopulationGuid.create(status.getSubpopulationGuid()), status);
    }
    
    @Test
    public void isUserConsented() {
        assertFalse(ConsentStatus.isUserConsented(statuses));
        
        add(TestConstants.REQUIRED_UNSIGNED);
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.OPTIONAL_SIGNED_CURRENT);
        add(TestConstants.OPTIONAL_SIGNED_OBSOLETE);
        assertFalse(ConsentStatus.isUserConsented(statuses));
        
        statuses.clear();
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.REQUIRED_UNSIGNED);
        add(TestConstants.OPTIONAL_UNSIGNED);
        add(TestConstants.OPTIONAL_SIGNED_OBSOLETE);
        assertFalse(ConsentStatus.isUserConsented(statuses));

        statuses.clear();
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.REQUIRED_SIGNED_OBSOLETE);
        add(TestConstants.OPTIONAL_UNSIGNED);
        add(TestConstants.OPTIONAL_SIGNED_OBSOLETE);
        assertTrue(ConsentStatus.isUserConsented(statuses));
    }

    @Test
    public void isConsentCurrent() {
        assertFalse(ConsentStatus.isConsentCurrent(statuses));
        
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.REQUIRED_SIGNED_OBSOLETE);
        add(TestConstants.OPTIONAL_UNSIGNED);
        assertFalse(ConsentStatus.isConsentCurrent(statuses));
        
        statuses.clear();
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.OPTIONAL_SIGNED_OBSOLETE); // only required consents are considered 
        assertTrue(ConsentStatus.isConsentCurrent(statuses));
        
        statuses.clear();
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.OPTIONAL_UNSIGNED);
        assertTrue(ConsentStatus.isConsentCurrent(statuses));
    }
    
    @Test
    public void hasOnlyOneSignedConsent() {
        assertFalse(ConsentStatus.hasOnlyOneSignedConsent(statuses));
        
        add(TestConstants.REQUIRED_UNSIGNED);
        add(TestConstants.OPTIONAL_SIGNED_OBSOLETE);
        assertTrue(ConsentStatus.hasOnlyOneSignedConsent(statuses));
        
        statuses.clear();
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.REQUIRED_SIGNED_OBSOLETE);
        add(TestConstants.REQUIRED_UNSIGNED);
        assertFalse(ConsentStatus.hasOnlyOneSignedConsent(statuses));
        
        statuses.clear();
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.REQUIRED_UNSIGNED);
        add(TestConstants.OPTIONAL_UNSIGNED);
        assertTrue(ConsentStatus.hasOnlyOneSignedConsent(statuses));
    }
}
