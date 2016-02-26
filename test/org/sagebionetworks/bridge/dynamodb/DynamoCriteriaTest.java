package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashSet;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.Criteria;

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
        criteria.setKey("subpopulation:AAA");
        criteria.setLanguage("fr");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(criteria);
        assertEquals(2, node.get("minAppVersion").asInt());
        assertEquals(8, node.get("maxAppVersion").asInt());
        assertEquals("fr", node.get("language").asText());
        assertEquals(SET_A, JsonUtils.asStringSet(node, "allOfGroups"));
        assertEquals(SET_B, JsonUtils.asStringSet(node, "noneOfGroups"));
        assertEquals("Criteria", node.get("type").asText());
        assertNull(node.get("key"));
        
        String json = makeJson("{'minAppVersion':2,'maxAppVersion':8,'language':'de','allOfGroups':['a','b'],'noneOfGroups':['c','d']}");
        
        Criteria crit = BridgeObjectMapper.get().readValue(json, Criteria.class);
        assertEquals(new Integer(2), crit.getMinAppVersion());
        assertEquals(new Integer(8), crit.getMaxAppVersion());
        assertEquals("de", crit.getLanguage());
        assertEquals(SET_A, crit.getAllOfGroups());
        assertEquals(SET_B, crit.getNoneOfGroups());
        assertNull(crit.getKey());
    }
    
    private String makeJson(String string) {
        return string.replaceAll("'", "\"");
    }
    
}
