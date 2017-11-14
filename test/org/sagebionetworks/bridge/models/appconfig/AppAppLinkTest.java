package org.sagebionetworks.bridge.models.appconfig;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.models.studies.AppleAppLink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

public class AppAppLinkTest {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Test
    public void test() throws Exception {
        AppleAppLink link = new AppleAppLink("appId", Lists.newArrayList("/studyId/","/studyId/*"));
        
        JsonNode node = MAPPER.valueToTree(link);
        assertEquals("appId", node.get("appID").textValue());
        assertEquals("/studyId/", node.get("paths").get(0).textValue());
        assertEquals("/studyId/*", node.get("paths").get(1).textValue());
        
        AppleAppLink deser = MAPPER.readValue(node.toString(), AppleAppLink.class);
        assertEquals("appId", deser.getAppId());
        assertEquals("/studyId/", deser.getPaths().get(0));
        assertEquals("/studyId/*", deser.getPaths().get(1));
    }
}
