package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContext;

import java.util.Map;

import org.junit.Test;

import play.mvc.Http;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.play.controllers.BaseController;

/** Test class for basic utility functions in BaseController. */
@SuppressWarnings("unchecked")
public class BaseControllerTest {
    private static final String DUMMY_JSON = "{\"dummy-key\":\"dummy-value\"}";

    @Test
    public void testParseJsonFromText() {
        // mock request
        Http.RequestBody mockBody = mock(Http.RequestBody.class);
        when(mockBody.asText()).thenReturn(DUMMY_JSON);

        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.body()).thenReturn(mockBody);

        // execute and validate
        Map<String, String> resultMap = BaseController.parseJson(mockRequest, Map.class);
        assertEquals(1, resultMap.size());
        assertEquals("dummy-value", resultMap.get("dummy-key"));
    }

    @Test
    public void testParseJsonFromNode() throws Exception {
        // mock request
        Http.RequestBody mockBody = mock(Http.RequestBody.class);
        when(mockBody.asText()).thenReturn(null);
        when(mockBody.asJson()).thenReturn(BridgeObjectMapper.get().readTree(DUMMY_JSON));

        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.body()).thenReturn(mockBody);

        // execute and validate
        Map<String, String> resultMap = BaseController.parseJson(mockRequest, Map.class);
        assertEquals(1, resultMap.size());
        assertEquals("dummy-value", resultMap.get("dummy-key"));
    }

    @Test(expected = InvalidEntityException.class)
    public void testParseJsonError() {
        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.body()).thenThrow(RuntimeException.class);
        BaseController.parseJson(mockRequest, Map.class);
    }

    @Test(expected = InvalidEntityException.class)
    public void testParseJsonNoJson() throws Exception {
        // mock request
        Http.RequestBody mockBody = mock(Http.RequestBody.class);
        when(mockBody.asText()).thenReturn(null);
        when(mockBody.asJson()).thenReturn(null);

        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.body()).thenReturn(mockBody);

        // execute and validate
        BaseController.parseJson(mockRequest, Map.class);
    }
    
    @Test
    public void canRetrieveClientInfoObject() throws Exception {
        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.getHeader(BridgeConstants.USER_AGENT_HEADER))
            .thenReturn("Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        Http.Context context = mockPlayContext();
        when(context.request()).thenReturn(mockRequest);
        Http.Context.current.set(context);
        
        ClientInfo info = new SchedulePlanController().getUserAgent();
        assertEquals("Asthma", info.getAppName());
        assertEquals(26, info.getAppVersion().intValue());
        assertEquals("Unknown iPhone", info.getOsName());
        assertEquals("iPhone OS 9.0.2", info.getOsVersion());
        assertEquals("BridgeSDK", info.getSdkName());
        assertEquals(4, info.getSdkVersion().intValue());
    }
    
    @Test
    public void doesNotThrowErrorWhenUserAgentStringInvalid() throws Exception {
        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.getHeader(BridgeConstants.USER_AGENT_HEADER))
            .thenReturn("Amazon Route 53 Health Check Service; ref:c97cd53f-2272-49d6-a8cd-3cd658d9d020; report http://amzn.to/1vsZADi");
        
        Http.Context context = mockPlayContext();
        when(context.request()).thenReturn(mockRequest);
        Http.Context.current.set(context);
        
        ClientInfo info = new SchedulePlanController().getUserAgent();
        assertNull(info.getAppName());
        assertNull(info.getAppVersion());
        assertNull(info.getOsName());
        assertNull(info.getOsVersion());
        assertNull(info.getSdkName());
        assertNull(info.getSdkVersion());
    }
}
