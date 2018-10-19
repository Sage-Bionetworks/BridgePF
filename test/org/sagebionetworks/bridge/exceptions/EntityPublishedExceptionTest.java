package org.sagebionetworks.bridge.exceptions;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.play.interceptors.ExceptionInterceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class EntityPublishedExceptionTest {

    @Test
    public void test() throws Exception {
        EntityPublishedException epe = new EntityPublishedException("This has been published");
        
        assertEquals(400, epe.getStatusCode());
        assertEquals("This has been published", epe.getMessage());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(epe);
        ((ObjectNode)node).remove(ExceptionInterceptor.UNEXPOSED_FIELD_NAMES);
        assertEquals(3, node.size());
        assertEquals(400, node.get("statusCode").intValue());
        assertEquals("This has been published", node.get("message").textValue());
        assertEquals("EntityPublishedException", node.get("type").textValue());
    }
}
