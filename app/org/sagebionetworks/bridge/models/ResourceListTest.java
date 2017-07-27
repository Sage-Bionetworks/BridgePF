package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

public class ResourceListTest {

    @Test
    public void canSerialize() throws Exception {
        List<StudyParticipant> summaries = Lists.newArrayList(new StudyParticipant.Builder().build());
        
        ResourceList<StudyParticipant> list = new ResourceList<>(summaries).withFilter("emailFilter", "dave");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        
        assertEquals("dave", node.get("emailFilter").asText());
        assertEquals("ResourceList", node.get("type").asText());
        assertEquals(4, node.size());
        
        ArrayNode array = (ArrayNode)node.get("items");
        assertEquals(1, array.size());
        
        ResourceList<StudyParticipant> deser = BridgeObjectMapper.get().readValue(node.toString(), new TypeReference<ResourceList<StudyParticipant>>() {});
        assertEquals("dave", (String)deser.getFilters().get("emailFilter"));
        assertEquals(1, deser.getItems().size());
        
        assertEquals("dave", deser.getRequestParams().get("emailFilter"));
    }
    
}
