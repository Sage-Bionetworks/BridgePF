package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.UserSession;

// These are just temporary tests to support the dummy implementation. Replace these with real tests when we write the
// real implementation.
public class MpowerVisualizationControllerTest {
    @Test
    public void test() throws Exception {
        // Spy controller. Mock session.
        MpowerVisualizationController controller = spy(new MpowerVisualizationController());
        doReturn(new UserSession()).when(controller).getAuthenticatedAndConsentedSession();

        // execute and validate
        Result result = controller.getVisualization("2016-02-01", "2016-02-03");
        assertEquals(200, result.status());

        String resultStr = Helpers.contentAsString(result);
        JsonNode resultNode = BridgeObjectMapper.get().readTree(resultStr);
        assertEquals(3, resultNode.size());

        for (int i = 1; i <= 3; i++) {
            JsonNode dateNode = resultNode.get("2016-02-0" + i);
            assertEquals(8, dateNode.size());

            for (String oneDataKey : MpowerVisualizationController.DATA_KEY_SET) {
                JsonNode dataValueNode = dateNode.get(oneDataKey);
                assertTrue(dataValueNode.isDouble());

                double dataValue = dataValueNode.doubleValue();
                assertTrue(dataValue >= 0.0);
                assertTrue(dataValue <= 1.0);
            }
        }
    }

    @Test(expected = BadRequestException.class)
    public void dateRangeTooWide() {
        // Spy controller. Mock session.
        MpowerVisualizationController controller = spy(new MpowerVisualizationController());
        doReturn(new UserSession()).when(controller).getAuthenticatedAndConsentedSession();

        // execute
        // Not gonna do the exact math, but this is definitely too long.
        controller.getVisualization("2016-01-01", "2016-03-01");
    }
}
