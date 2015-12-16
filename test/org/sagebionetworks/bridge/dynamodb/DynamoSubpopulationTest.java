package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoSubpopulationTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoSubpopulation.class).suppress(Warning.NONFINAL_FIELDS)
            .allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        Subpopulation subpop = new DynamoSubpopulation();
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setGuidString("guid");
        subpop.setStudyIdentifier("study-key");
        subpop.setMinAppVersion(2);
        subpop.setMaxAppVersion(10);
        subpop.setRequired(true);
        subpop.setDefaultGroup(true);
        subpop.setDeleted(true);
        subpop.getAllOfGroups().add("requiredGroup");
        subpop.getNoneOfGroups().add("prohibitedGroup");
        subpop.setVersion(3L);
        
        String json = BridgeObjectMapper.get().writeValueAsString(subpop);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        // This does not need to be passed to the user; the user is never allowed to set it.
        // This should be standard across the API, BTW, but this is leaked out by some classes.
        assertNull(node.get("studyIdentifier"));
        assertEquals("Name", node.get("name").asText());
        assertEquals("Description", node.get("description").asText());
        assertEquals("guid", node.get("guid").asText());
        assertEquals(2, node.get("minAppVersion").asInt());
        assertEquals(10, node.get("maxAppVersion").asInt());
        assertTrue(node.get("required").asBoolean());
        assertTrue(node.get("defaultGroup").asBoolean());
        assertNull(node.get("deleted")); // users do not see this flag, they never get deleted items
        assertEquals("requiredGroup", node.get("allOfGroups").get(0).asText());
        assertEquals("prohibitedGroup", node.get("noneOfGroups").get(0).asText());
        assertEquals(3L, node.get("version").asLong());
        
        Subpopulation newSubpop = BridgeObjectMapper.get().readValue(json, Subpopulation.class);
        // Not serialized, these values have to be added back to have equal objects 
        newSubpop.setStudyIdentifier("study-key");
        newSubpop.setDeleted(true);
        
        assertEquals(subpop, newSubpop);
        
        // Finally, check the publication site URLs
        assertEqualsAndNotNull(newSubpop.getConsentHTML(), JsonUtils.asText(node, "consentHTML"));
        assertEqualsAndNotNull(newSubpop.getConsentPDF(), JsonUtils.asText(node, "consentPDF"));

        String htmlURL = "http://" + BridgeConfigFactory.getConfig().getHostnameWithPostfix("docs") + "/" + newSubpop.getGuidString() + "/consent.html";
        assertEquals(htmlURL, newSubpop.getConsentHTML());
        
        String pdfURL = "http://" + BridgeConfigFactory.getConfig().getHostnameWithPostfix("docs") + "/" + newSubpop.getGuidString() + "/consent.pdf";
        assertEquals(pdfURL, newSubpop.getConsentPDF());
    }
    
    void assertEqualsAndNotNull(Object expected, Object actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected, actual);
    }
    
}
