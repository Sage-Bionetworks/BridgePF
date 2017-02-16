package org.sagebionetworks.bridge.exceptions;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;

import java.util.Map;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.play.interceptors.ExceptionInterceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class EntityAlreadyExistsExceptionTest {

    @Mock
    MethodInvocation invocation;
    
    /**
     * Some entities are not exposed through the API and when such an internal entity already exists, we cannot return
     * the object the user just submitted to us. The exception should still work.
     */
    @Test
    public void exceptionSerializesWithoutEntity() throws Exception {
        EntityAlreadyExistsException e = new EntityAlreadyExistsException(ExternalIdentifier.class, null);
        assertEquals("ExternalIdentifier already exists.", e.getMessage());
        assertEquals(0, e.getEntity().entrySet().size());
        assertEquals("ExternalIdentifier", e.getEntityClass());
    }
    
    @Test
    public void exceptionWithEntityKeys() throws Exception {
        Map<String,Object> map = new ImmutableMap.Builder<String,Object>().put("key", "value").build();
        EntityAlreadyExistsException e = new EntityAlreadyExistsException(ExternalIdentifier.class, map);
        
        assertEquals("ExternalIdentifier already exists.", e.getMessage());
        assertEquals(1, e.getEntity().entrySet().size());
        assertEquals("value", e.getEntity().get("key"));
        assertEquals("ExternalIdentifier", e.getEntityClass());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void exceptionInvalidConstruction() {
        new EntityAlreadyExistsException(null, null);
    }
    
    @Test
    public void serializesCorrectlyThroughInterceptor() throws Throwable {
        EntityAlreadyExistsException e = new EntityAlreadyExistsException(ExternalIdentifier.class, "identifier", "foo");
        
        ExceptionInterceptor interceptor = new ExceptionInterceptor();
        
        TestUtils.mockPlayContext();
        doThrow(e).when(invocation).proceed();
        
        Result result = (Result)interceptor.invoke(invocation);
        
        assertEquals(409, result.status());
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        
        assertEquals("ExternalIdentifier already exists.", node.get("message").asText());
        assertEquals("EntityAlreadyExistsException", node.get("type").asText());
        assertEquals("ExternalIdentifier", node.get("entityClass").asText());
        assertEquals(409, node.get("statusCode").asInt());
        
        JsonNode entity = node.get("entity");
        assertEquals("foo", entity.get("identifier").asText());
        
        JsonNode entityKeys = node.get("entityKeys");
        assertEquals("foo", entityKeys.get("identifier").asText());
        assertEquals(6, node.size());
    }
}
