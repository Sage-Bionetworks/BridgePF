package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Test;
import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.MpowerVisualizationService;

public class MpowerVisualizationControllerTest {
    private static final String DUMMY_HEALTH_CODE = "dummyHealthCode";

    @Test
    public void test() throws Exception {
        // Spy controller. Mock session.
        User user = new User();
        user.setHealthCode(DUMMY_HEALTH_CODE);

        UserSession session = new UserSession();
        session.setUser(user);

        MpowerVisualizationController controller = spy(new MpowerVisualizationController());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();

        // mock service - For rapid development and to avoid unnecessary coupling, just mock the visualization with a
        // dummy string. Check if that dummy string is propagated through the controller.
        MpowerVisualizationService mockSvc = mock(MpowerVisualizationService.class);
        JsonNode mockViz = new TextNode("mock visualization");
        when(mockSvc.getVisualization(DUMMY_HEALTH_CODE, "2016-02-06", "2016-02-08"))
                .thenReturn(mockViz);

        controller.setMpowerVisualizationService(mockSvc);

        // execute and validate
        Result result = controller.getVisualization("2016-02-06", "2016-02-08");
        assertEquals(200, result.status());

        String resultStr = Helpers.contentAsString(result);
        JsonNode resultNode = BridgeObjectMapper.get().readTree(resultStr);
        assertEquals("mock visualization", resultNode.textValue());
    }
}
