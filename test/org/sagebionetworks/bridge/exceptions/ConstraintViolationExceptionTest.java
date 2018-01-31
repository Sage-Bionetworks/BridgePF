package org.sagebionetworks.bridge.exceptions;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.play.interceptors.ExceptionInterceptor;

import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class ConstraintViolationExceptionTest {

    @Mock
    MethodInvocation invocation;
    
    @Test
    public void testConstruction() {
        ConstraintViolationException e = createException();
        
        assertEquals("Referenced in survey", e.getMessage());
        assertEquals("surveyGuidValue", e.getReferrerKeys().get("surveyGuid"));
        assertEquals("createdOnValue", e.getReferrerKeys().get("createdOn"));
        assertEquals("schemaIdValue", e.getEntityKeys().get("schemaId"));
        assertEquals("10", e.getEntityKeys().get("schemaRevision"));
    }
    
    @Test
    public void serializesCorrectlyThroughInterceptor() throws Throwable {
        ConstraintViolationException e = createException();
        
        ExceptionInterceptor interceptor = new ExceptionInterceptor();
        
        TestUtils.mockPlayContext();
        doThrow(e).when(invocation).proceed();
        
        Result result = (Result)interceptor.invoke(invocation);
        TestUtils.assertResult(result, 409);
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        
        assertEquals("Referenced in survey", node.get("message").asText());
        assertEquals("ConstraintViolationException", node.get("type").asText());
        assertEquals(409, node.get("statusCode").asInt());
        
        JsonNode entityKeys = node.get("entityKeys");
        assertEquals("schemaIdValue", entityKeys.get("schemaId").asText());
        assertEquals("10", entityKeys.get("schemaRevision").asText());
        
        JsonNode referrerKeys = node.get("referrerKeys");
        assertEquals("surveyGuidValue", referrerKeys.get("surveyGuid").asText());
        assertEquals("createdOnValue", referrerKeys.get("createdOn").asText());
        assertEquals(5, node.size());
    }
    
    @Test
    public void hasDefaultMessage() {
        ConstraintViolationException e = new ConstraintViolationException.Builder()
                .withReferrerKey("surveyGuid", "surveyGuidValue")
                .withReferrerKey("createdOn", "createdOnValue")
                .withEntityKey("schemaId", "schemaIdValue")
                .withEntityKey("schemaRevision", "10").build();
        
        assertEquals("Operation not permitted because entity {surveyGuid=surveyGuidValue, createdOn=createdOnValue} refers to this entity {schemaId=schemaIdValue, schemaRevision=10}.", e.getMessage());
    }
    
    @Test
    public void collectionsExistEvenIfEmpty() throws Throwable {
        ConstraintViolationException e = new ConstraintViolationException.Builder()
                .withMessage("Referenced in survey").build();
        
        ExceptionInterceptor interceptor = new ExceptionInterceptor();
        
        TestUtils.mockPlayContext();
        doThrow(e).when(invocation).proceed();
        
        Result result = (Result)interceptor.invoke(invocation);

        TestUtils.assertResult(result, 409);
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        
        assertEquals(5, node.size());
        assertEquals(0, node.get("entityKeys").size());
        assertEquals(0, node.get("referrerKeys").size());
    }

    private ConstraintViolationException createException() {
        return new ConstraintViolationException.Builder()
                .withMessage("Referenced in survey")
                .withReferrerKey("surveyGuid", "surveyGuidValue")
                .withReferrerKey("createdOn", "createdOnValue")
                .withEntityKey("schemaId", "schemaIdValue")
                .withEntityKey("schemaRevision", "10").build();
    }
}
