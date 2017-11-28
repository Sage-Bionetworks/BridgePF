package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.models.studies.AppleAppLink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

public class AppleAppSiteAssociationTest {

    @Test
    public void createsCorrectJson() throws Exception {
        AppleAppLink detail1 = new AppleAppLink("appId1", Lists.newArrayList("/appId1/","/appId1/*"));
        AppleAppLink detail2 = new AppleAppLink("appId2", Lists.newArrayList("/appId2/","/appId2/*"));
        
        AppleAppSiteAssociation assoc = new AppleAppSiteAssociation(Lists.newArrayList(detail1, detail2));
        
        JsonNode node = new ObjectMapper().valueToTree(assoc);
        
        JsonNode applinks = node.get("applinks"); // It's all under this property for some reason.
        
        assertEquals(0, ((ArrayNode)applinks.get("apps")).size());
        
        JsonNode details = applinks.get("details");
        
        JsonNode node1 = details.get(0);
        assertEquals("appId1", node1.get("appID").textValue());
        assertEquals("/appId1/", node1.get("paths").get(0).textValue());
        assertEquals("/appId1/*", node1.get("paths").get(1).textValue());
        
        JsonNode node2 = details.get(1);
        assertEquals("appId2", node2.get("appID").textValue());
        assertEquals("/appId2/", node2.get("paths").get(0).textValue());
        assertEquals("/appId2/*", node2.get("paths").get(1).textValue());
    }

}
