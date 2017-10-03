package org.sagebionetworks.bridge.play.interceptors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static play.test.Helpers.contentAsString;

import java.util.Map;

import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputExceededException;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.amazonaws.AmazonServiceException.ErrorType;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import play.mvc.Http;
import play.mvc.Result;

public class ExceptionInterceptorTest {

    private ExceptionInterceptor interceptor;
    
    @Before
    public void before() throws Exception {
        interceptor = new ExceptionInterceptor();
        mockContext();
    }
    
    private Map<String,String[]> map(String[] values) {
        Map<String,String[]> map = Maps.newHashMap();
        for (int i=0; i <= values.length-2; i+=2) {
            map.put(values[i], new String[] { values[i+1] });
        }
        return map;
    }
    
    private void mockContext(String... values) throws Exception {
        Map<String,String[]> map = map(values);
        
        Http.Request request = mock(Http.Request.class);
        when(request.queryString()).thenReturn(map);
        
        Http.Context context = mock(Http.Context.class);
        when(context.request()).thenReturn(request);

        Http.Context.current.set(context);
    }
    
    @Test
    public void consentRequiredSessionSerializedCorrectly() throws Throwable {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail("email@email.com")
                .withFirstName("firstName")
                .withLastName("lastName")
                .withHealthCode("healthCode")
                .withNotifyByEmail(true)
                .withId("userId")
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withDataGroups(Sets.newHashSet("group1")).build();
        
        UserSession session = new UserSession(participant);
        session.setAuthenticated(true);
        session.setEnvironment(Environment.DEV);
        session.setInternalSessionToken("internalToken");
        session.setSessionToken("sessionToken");
        session.setReauthToken("reauthToken");
        session.setStudyIdentifier(new StudyIdentifierImpl("test"));
        session.setConsentStatuses(Maps.newHashMap());
        
        ConsentRequiredException exception = new ConsentRequiredException(session);
        
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.proceed()).thenThrow(exception);
        
        Result result = (Result)interceptor.invoke(invocation);
        JsonNode node = new ObjectMapper().readTree(contentAsString(result));

        assertTrue(node.get("authenticated").booleanValue());
        assertFalse(node.get("consented").booleanValue());
        assertFalse(node.get("signedMostRecentConsent").booleanValue());
        assertEquals("all_qualified_researchers", node.get("sharingScope").textValue());
        assertEquals("sessionToken", node.get("sessionToken").textValue());
        assertEquals("develop", node.get("environment").textValue());
        assertEquals("email@email.com", node.get("username").textValue());
        assertEquals("email@email.com", node.get("email").textValue());
        assertEquals("userId", node.get("id").textValue());
        assertEquals("reauthToken", node.get("reauthToken").textValue());
        assertTrue(node.get("dataSharing").booleanValue());
        assertTrue(node.get("notifyByEmail").booleanValue());
        assertEquals("UserSessionInfo", node.get("type").textValue());
        ArrayNode array = (ArrayNode)node.get("roles");
        assertEquals(0, array.size());
        array = (ArrayNode)node.get("dataGroups");
        assertEquals(1, array.size());
        assertEquals("group1", array.get(0).textValue());
        assertEquals(0, node.get("consentStatuses").size());
        // And no further properties
        assertEquals(20, node.size());

        // Don't use assertStatusCode(), because this returns a session, not an exception. Still want to verify the
        // Result status code though.
        assertEquals(412, result.status());
    }
    
    @Test
    public void amazonServiceMessageCorrectlyReported() throws Throwable {
        Map<String,String> map = Maps.newHashMap();
        map.put("A", "B");
        
        // We're verifying that we suppress everything here except fields that are unique and important
        // in the exception.
        AmazonDynamoDBException exc = new AmazonDynamoDBException("This is not the final message?");
        exc.setStatusCode(400);
        exc.setErrorMessage("This is an error message.");
        exc.setErrorType(ErrorType.Client);
        exc.setRawResponseContent("rawResponseContent");
        exc.setRawResponse("rawResponseContent".getBytes());
        exc.setErrorCode("someErrorCode");
        exc.setHttpHeaders(map);
        exc.setRequestId("abd");
        exc.setServiceName("serviceName");
        
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.proceed()).thenThrow(exc);
        
        Result result = (Result)interceptor.invoke(invocation);
        JsonNode node = new ObjectMapper().readTree(contentAsString(result));
        
        assertEquals(3, node.size()); 
        assertEquals("This is an error message.", node.get("message").textValue());
        assertEquals("BadRequestException", node.get("type").textValue());

        assertStatusCode(400, result, node);
    }

    @Test
    public void ddbThrottlingReportedAs500() throws Throwable {
        // ProvisionedThroughputExceededException from Amazon reports itself as a 400, but we want to treat it as a 500
        ProvisionedThroughputExceededException ex = new ProvisionedThroughputExceededException(
                "dummy exception message");
        ex.setStatusCode(400);

        // Set up invocation.
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.proceed()).thenThrow(ex);

        // Execute and validate - Just test the status code and type. Everything else is tested elsewhere.
        Result result = (Result) interceptor.invoke(invocation);
        JsonNode node = new ObjectMapper().readTree(contentAsString(result));
        assertEquals("BridgeServiceException", node.get("type").textValue());

        assertStatusCode(500, result, node);
    }

    @Test
    public void bridgeServiceExceptionCorrectlyReported() throws Throwable {
        Study study = new DynamoStudy();
        try {
            Validate.entityThrowingException(new StudyValidator(), study); 
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            MethodInvocation invocation = mock(MethodInvocation.class);
            when(invocation.proceed()).thenThrow(e);
            
            Result result = (Result)interceptor.invoke(invocation);
            JsonNode node = new ObjectMapper().readTree(contentAsString(result));
            
            assertEquals(5, node.size());
            assertEquals("identifier is required", node.get("errors").get("identifier").get(0).textValue());
            assertEquals("InvalidEntityException", node.get("type").textValue());
            assertNotNull(node.get("entity"));
            assertNotNull(node.get("errors"));

            assertStatusCode(400, result, node);
        }
    }

    private static void assertStatusCode(int expectedStatus, Result result, JsonNode node) {
        assertEquals(expectedStatus, result.status());
        assertEquals(expectedStatus, node.get("statusCode").intValue());
    }
}
