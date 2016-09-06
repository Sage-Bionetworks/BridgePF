package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;

import java.util.HashSet;
import java.util.Map;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.Criteria;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;
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
        assertEquals(2, minValues.get(IOS).asInt());
        assertEquals(10, minValues.get(ANDROID).asInt());
        
        JsonNode maxValues = node.get("maxAppVersions");
        assertEquals(8, maxValues.get(IOS).asInt());
        assertEquals(15, maxValues.get(ANDROID).asInt());
        
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
        Criteria criteria = Criteria.create();
        criteria.setMinAppVersion(ANDROID, 10);
        criteria.setMaxAppVersion(ANDROID, 15);

        criteria.setMinAppVersion(IOS, null);
        criteria.setMaxAppVersion(ANDROID, null);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(criteria);
        assertNull(node.get("minAppVersions").get(IOS));
        assertNull(node.get("maxAppVersions").get(ANDROID));
    }
    
    @Test
    public void newerPlatformVersionAttributesTakePrecedenceOverLegacyProperties() {
        // Use DynamoCriteria directly so you can set legacy values
        DynamoCriteria criteria = new DynamoCriteria();
        criteria.setMinAppVersion(IOS, 8);
        criteria.setMinAppVersion(4);
        // Using legacy setter does not set value if it already exists in the map
        assertEquals(new Integer(8), criteria.getMinAppVersion(IOS));
        
        // But of course you can update the value in the map
        criteria.setMinAppVersion(IOS, 10);
        assertEquals(new Integer(10), criteria.getMinAppVersion(IOS));
    }
    
    @Test
    public void cannotSetNullValuesInPlatformVersionMap() {
        Map<String, Integer> map = Maps.newHashMap();
        map.put(IOS, null);
        
        DynamoCriteria criteria = new DynamoCriteria();
        criteria.setMinAppVersions(map);
        criteria.setMaxAppVersions(map);
        assertFalse(criteria.getMinAppVersions().containsKey(IOS));
        assertFalse(criteria.getMaxAppVersions().containsKey(IOS));
        
        criteria.setMinAppVersion(IOS, null);
        criteria.setMaxAppVersion(IOS, null);
        assertFalse(criteria.getMinAppVersions().containsKey(IOS));
        assertFalse(criteria.getMaxAppVersions().containsKey(IOS));
        
        criteria.setMinAppVersion(null);
        criteria.setMaxAppVersion(null);
        assertFalse(criteria.getMinAppVersions().containsKey(IOS));
        assertFalse(criteria.getMaxAppVersions().containsKey(IOS));
    }
    
    private String makeJson(String string) {
        return string.replaceAll("'", "\"");
    }
    
}
