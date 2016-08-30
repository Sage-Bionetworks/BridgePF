package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;

import java.util.HashSet;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.OperatingSystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoCriteriaTest {

    private static final HashSet<String> SET_B = Sets.newHashSet("c","d");
    private static final HashSet<String> SET_A = Sets.newHashSet("a","b");
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoCriteria.class).suppress(Warning.NONFINAL_FIELDS)
            .allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canSerialize() throws Exception {
        Criteria criteria = TestUtils.createCriteria(2, 8, SET_A, SET_B);
        criteria.setMinAppVersion(ANDROID, 10);
        criteria.setMaxAppVersion(ANDROID, 15);
        criteria.setKey("subpopulation:AAA");
        criteria.setLanguage("fr");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(criteria);
        assertEquals("fr", node.get("language").asText());
        assertEquals(SET_A, JsonUtils.asStringSet(node, "allOfGroups"));
        assertEquals(SET_B, JsonUtils.asStringSet(node, "noneOfGroups"));
        
        JsonNode minValues = node.get("minAppVersions");
        assertEquals(2, minValues.get(OperatingSystem.IOS).asInt());
        assertEquals(10, minValues.get(OperatingSystem.ANDROID).asInt());
        
        JsonNode maxValues = node.get("maxAppVersions");
        assertEquals(8, maxValues.get(OperatingSystem.IOS).asInt());
        assertEquals(15, maxValues.get(OperatingSystem.ANDROID).asInt());
        
        assertEquals("Criteria", node.get("type").asText());
        assertNull(node.get("key"));
        assertEquals(6, node.size()); // Nothing else is serialized here. (That's important.)
        
        // However, we will except the older variant of JSON for the time being
        String json = makeJson("{'minAppVersion':2,'maxAppVersion':8,'language':'de','allOfGroups':['a','b'],'noneOfGroups':['c','d']}");
        
        Criteria crit = BridgeObjectMapper.get().readValue(json, Criteria.class);
        assertEquals(new Integer(2), crit.getMinAppVersion(IOS));
        assertEquals(new Integer(8), crit.getMaxAppVersion(IOS));
        assertEquals("de", crit.getLanguage());
        assertEquals(SET_A, crit.getAllOfGroups());
        assertEquals(SET_B, crit.getNoneOfGroups());
        assertNull(crit.getKey());
    }
    
    @Test
    public void canRemoveMinMaxAttributes() {
        Criteria criteria = TestUtils.createCriteria(2, 8, SET_A, SET_B);
        criteria.setMinAppVersion(ANDROID, 10);
        criteria.setMaxAppVersion(ANDROID, 15);
        criteria.setKey("subpopulation:AAA");
        criteria.setLanguage("fr");

        criteria.setMinAppVersion(IOS, null);
        criteria.setMaxAppVersion(ANDROID, null);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(criteria);
        assertNull(node.get("minAppVersions").get("ios"));
        assertNull(node.get("maxAppVersions").get("android"));
    }
    
    private String makeJson(String string) {
        return string.replaceAll("'", "\"");
    }
    
}
