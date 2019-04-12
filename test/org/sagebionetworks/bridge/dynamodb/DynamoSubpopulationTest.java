package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;

import java.util.Set;

import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoSubpopulationTest {
    
    private static final Set<String> ALL_OF_GROUPS = Sets.newHashSet("requiredGroup");
    private static final Set<String> NONE_OF_GROUPS = Sets.newHashSet("prohibitedGroup");
    private static final DateTime PUBLISHED_CONSENT_TIMESTAMP = DateTime.parse("2016-07-12T19:49:07.415Z");

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoSubpopulation.class).suppress(Warning.NONFINAL_FIELDS)
            .allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        Subpopulation subpop = makeSubpopulation();
        
        Criteria criteria = TestUtils.createCriteria(2, 10, ALL_OF_GROUPS, NONE_OF_GROUPS);
        subpop.setCriteria(criteria);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(subpop);

        // This does not need to be passed to the user; the user is never allowed to set it.
        // This should be standard across the API, BTW, but this is leaked out by some classes.
        assertEquals("Name", node.get("name").textValue());
        assertEquals("Description", node.get("description").textValue());
        assertEquals("guid", node.get("guid").textValue());
        assertEquals(PUBLISHED_CONSENT_TIMESTAMP.toString(), node.get("publishedConsentCreatedOn").textValue());
        assertTrue(node.get("required").booleanValue());
        assertTrue(node.get("defaultGroup").booleanValue());
        assertTrue(node.get("autoSendConsentSuppressed").booleanValue());
        assertTrue(node.get("deleted").booleanValue()); // users do not see this flag, they never get deleted items
        assertEquals("study-key", node.get("studyIdentifier").textValue());
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(3L, node.get("version").longValue());
        
        Set<String> dataGroups = ImmutableSet.of(node.get("dataGroupsAssignedWhileConsented").get(0).textValue(),
                node.get("dataGroupsAssignedWhileConsented").get(1).textValue());                
        assertEquals(TestConstants.USER_DATA_GROUPS, dataGroups);
        
        Set<String> substudyIds = ImmutableSet.of(node.get("substudyIdsAssignedOnConsent").get(0).textValue(),
                node.get("substudyIdsAssignedOnConsent").get(1).textValue());
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, substudyIds);
        assertEquals("Subpopulation", node.get("type").textValue());
        
        JsonNode critNode = node.get("criteria");
        assertEquals(ALL_OF_GROUPS, JsonUtils.asStringSet(critNode, "allOfGroups"));
        assertEquals(NONE_OF_GROUPS, JsonUtils.asStringSet(critNode, "noneOfGroups"));
        assertEquals(2, critNode.get("minAppVersions").get(IOS).asInt());
        assertEquals(10, critNode.get("maxAppVersions").get(IOS).asInt());
        
        Subpopulation newSubpop = BridgeObjectMapper.get().treeToValue(node, Subpopulation.class);
        // Not serialized, these values have to be added back to have equal objects 
        newSubpop.setStudyIdentifier("study-key");
        
        assertEquals(subpop, newSubpop);
        
        // Finally, check the publication site URLs
        assertEqualsAndNotNull(newSubpop.getConsentHTML(), JsonUtils.asText(node, "consentHTML"));
        assertEqualsAndNotNull(newSubpop.getConsentPDF(), JsonUtils.asText(node, "consentPDF"));

        String htmlURL = "http://" + BridgeConfigFactory.getConfig().getHostnameWithPostfix("docs") + "/" + newSubpop.getGuidString() + "/consent.html";
        assertEquals(htmlURL, newSubpop.getConsentHTML());
        
        String pdfURL = "http://" + BridgeConfigFactory.getConfig().getHostnameWithPostfix("docs") + "/" + newSubpop.getGuidString() + "/consent.pdf";
        assertEquals(pdfURL, newSubpop.getConsentPDF());
        
        Criteria critObject = newSubpop.getCriteria();
        assertEquals(new Integer(2), critObject.getMinAppVersion(IOS));
        assertEquals(new Integer(10), critObject.getMaxAppVersion(IOS));
        assertEquals(ALL_OF_GROUPS, critObject.getAllOfGroups());
        assertEquals(NONE_OF_GROUPS, critObject.getNoneOfGroups());
    }

    private Subpopulation makeSubpopulation() {
        Subpopulation subpop = new DynamoSubpopulation();
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setGuidString("guid");
        subpop.setStudyIdentifier("study-key");
        subpop.setRequired(true);
        subpop.setDefaultGroup(true);
        subpop.setPublishedConsentCreatedOn(PUBLISHED_CONSENT_TIMESTAMP.getMillis());
        subpop.setDeleted(true);
        subpop.setAutoSendConsentSuppressed(true);
        subpop.setVersion(3L);
        subpop.setDataGroupsAssignedWhileConsented(TestConstants.USER_DATA_GROUPS);
        subpop.setSubstudyIdsAssignedOnConsent(TestConstants.USER_SUBSTUDY_IDS);
        return subpop;
    }
    
    @Test
    public void cannotNullifySets() {
        Subpopulation subpop = new DynamoSubpopulation();
        // Set some values to verify that null resets these to the empty set
        subpop.setDataGroupsAssignedWhileConsented(ImmutableSet.of("A"));
        subpop.setSubstudyIdsAssignedOnConsent(ImmutableSet.of("B"));
        assertEquals(ImmutableSet.of("A"), subpop.getDataGroupsAssignedWhileConsented());
        assertEquals(ImmutableSet.of("B"), subpop.getSubstudyIdsAssignedOnConsent());
        
        subpop.setDataGroupsAssignedWhileConsented(null);
        subpop.setSubstudyIdsAssignedOnConsent(null);
        assertTrue(subpop.getDataGroupsAssignedWhileConsented().isEmpty());
        assertTrue(subpop.getSubstudyIdsAssignedOnConsent().isEmpty());
    }
    
    @Test
    public void publicInterfaceWriterWorks() throws Exception {
        Subpopulation subpop = makeSubpopulation();

        String json = Subpopulation.SUBPOP_WRITER.writeValueAsString(subpop);
        JsonNode node = BridgeObjectMapper.get().readTree(json);

        assertNull(node.get("studyIdentifier"));
    }
    
    @Test
    public void guidAndGuidStringInterchangeable() throws Exception {
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid(SubpopulationGuid.create("abc"));
        assertEquals("abc", subpop.getGuid().getGuid());
        
        String json = BridgeObjectMapper.get().writeValueAsString(subpop);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("abc", node.get("guid").asText());
        assertNull(node.get("guidString"));
        
        Subpopulation newSubpop = BridgeObjectMapper.get().readValue(json, Subpopulation.class);
        assertEquals("abc", newSubpop.getGuidString());
        assertEquals("abc", newSubpop.getGuid().getGuid());

        subpop = Subpopulation.create();
        subpop.setGuidString("abc");
        assertEquals("abc", subpop.getGuidString());
        
        json = BridgeObjectMapper.get().writeValueAsString(subpop);
        node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("abc", node.get("guid").asText());
        assertNull(node.get("guidString"));
        
        newSubpop = BridgeObjectMapper.get().readValue(json, Subpopulation.class);
        assertEquals("abc", newSubpop.getGuidString());
        assertEquals("abc", newSubpop.getGuid().getGuid());
    }
    
    void assertEqualsAndNotNull(Object expected, Object actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected, actual);
    }
    
}
