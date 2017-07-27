package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

public class ResourceListTest {

    @SuppressWarnings("deprecation")
    @Test
    public void canSerialize() throws Exception {
        
        ResourceList<String> list = new ResourceList<>(ImmutableList.of("A","B","C"))
                .withRequestParam("test", 13L);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        
        assertEquals(4, node.size());
        assertEquals(3, node.get("total").intValue());
        assertEquals(13, node.get("requestParams").get("test").intValue());
        assertEquals(3, node.get("items").size());
        assertEquals("ResourceList", node.get("type").asText());
        
        ResourceList<String> deser = BridgeObjectMapper.get().readValue(node.toString(), new TypeReference<ResourceList<String>>() {});
        
        assertEquals("A", deser.getItems().get(0));
        assertEquals("B", deser.getItems().get(1));
        assertEquals("C", deser.getItems().get(2));
        // This is deserialized as an integer, not a long, that is a property of the library. Looks the same in JSON.
        assertEquals((Integer)13, (Integer)deser.getRequestParams().get("test"));
        assertEquals((Integer)3, deser.getTotal());
    }
    
    @Test
    public void noTotalPropertyWhenListEmpty() {
        ResourceList<String> list = new ResourceList<>(ImmutableList.of());
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertFalse(node.has("total"));
        
        ResourceList<String> list2 = new ResourceList<>(null);
        JsonNode node2 = BridgeObjectMapper.get().valueToTree(list2);
        assertFalse(node2.has("total"));
    }
}
