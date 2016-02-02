package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContext;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import play.mvc.Http;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnsupportedVersionException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.play.controllers.BaseController;
import org.sagebionetworks.bridge.services.StudyConsentService;
import org.sagebionetworks.bridge.services.SubpopulationService;

import com.google.common.collect.Sets;

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
        
        ClientInfo info = new SchedulePlanController().getClientInfoFromUserAgentHeader();
        assertEquals("Asthma", info.getAppName());
        assertEquals(26, info.getAppVersion().intValue());
        assertEquals("iPhone OS", info.getOsName());
        assertEquals("9.0.2", info.getOsVersion());
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
        
        ClientInfo info = new SchedulePlanController().getClientInfoFromUserAgentHeader();
        assertNull(info.getAppName());
        assertNull(info.getAppVersion());
        assertNull(info.getOsName());
        assertNull(info.getOsVersion());
        assertNull(info.getSdkName());
        assertNull(info.getSdkVersion());
    }
    
    @Test (expected = UnsupportedVersionException.class)
    public void testInvalidSupportedVersionThrowsException() throws Exception {
        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.getHeader(BridgeConstants.USER_AGENT_HEADER))
            .thenReturn("Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        Http.Context context = mockPlayContext();
        when(context.request()).thenReturn(mockRequest);
        Http.Context.current.set(context);
        
        HashMap<String, Integer> map =new HashMap<>();
        map.put("iPhone OS", 28);
        
        Study study = mock(Study.class);
        when(study.getMinSupportedAppVersions()).thenReturn(map);
        
        SchedulePlanController controller = new SchedulePlanController();
        controller.verifySupportedVersionOrThrowException(study);

    }
    
    @Test
    public void testValidSupportedVersionDoesNotThrowException() throws Exception {
        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.getHeader(BridgeConstants.USER_AGENT_HEADER))
            .thenReturn("Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        Http.Context context = mockPlayContext();
        when(context.request()).thenReturn(mockRequest);
        Http.Context.current.set(context);
        
        HashMap<String, Integer> map =new HashMap<>();
        map.put("iPhone OS", 25);
        
        Study study = mock(Study.class);
        when(study.getMinSupportedAppVersions()).thenReturn(map);
        
        SchedulePlanController controller = new SchedulePlanController();
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test
    public void testNullSupportedVersionDoesNotThrowException() throws Exception {
        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.getHeader(BridgeConstants.USER_AGENT_HEADER))
            .thenReturn("Asthma/26 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4");
        
        Http.Context context = mockPlayContext();
        when(context.request()).thenReturn(mockRequest);
        Http.Context.current.set(context);
        
        HashMap<String, Integer> map =new HashMap<>();
        
        Study study = mock(Study.class);
        when(study.getMinSupportedAppVersions()).thenReturn(map);
        
        SchedulePlanController controller = new SchedulePlanController();
        controller.verifySupportedVersionOrThrowException(study);
    }
    
    @Test
    public void testUnknownOSDoesNotThrowException() throws Exception {
        Http.Request mockRequest = mock(Http.Request.class);
        when(mockRequest.getHeader(BridgeConstants.USER_AGENT_HEADER))
            .thenReturn("Asthma/26 BridgeSDK/4");
        
        Http.Context context = mockPlayContext();
        when(context.request()).thenReturn(mockRequest);
        Http.Context.current.set(context);
        
        HashMap<String, Integer> map =new HashMap<>();
        map.put("iPhone OS", 25);
        
        Study study = mock(Study.class);
        when(study.getMinSupportedAppVersions()).thenReturn(map);
        
        SchedulePlanController controller = new SchedulePlanController();
        controller.verifySupportedVersionOrThrowException(study);
    }

}
