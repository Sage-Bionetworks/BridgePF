package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.visualization.MpowerVisualization;
import org.sagebionetworks.bridge.services.MpowerVisualizationService;

public class MpowerVisualizationControllerTest {
    private static final String DUMMY_HEALTH_CODE = "dummyHealthCode";

    @Test
    public void nullStartDate() throws Exception {
        test(null, "2016-02-08", null, LocalDate.parse("2016-02-08"));
    }

    @Test
    public void emptyStartDate() throws Exception {
        test("", "2016-02-08", null, LocalDate.parse("2016-02-08"));
    }

    @Test
    public void blankStartDate() throws Exception {
        test("   ", "2016-02-08", null, LocalDate.parse("2016-02-08"));
    }

    @Test(expected = BadRequestException.class)
    public void malformedStartDate() throws Exception {
        test("this is not a date", "2016-02-08", null, LocalDate.parse("2016-02-08"));
    }

    @Test(expected = BadRequestException.class)
    public void timestampAsStartDate() throws Exception {
        test("2016-02-06T12:30-0800", "2016-02-08", null, LocalDate.parse("2016-02-08"));
    }

    @Test
    public void nullEndDate() throws Exception {
        test("2016-02-06", null, LocalDate.parse("2016-02-06"), null);
    }

    @Test
    public void emptyEndDate() throws Exception {
        test("2016-02-06", "", LocalDate.parse("2016-02-06"), null);
    }

    @Test
    public void blankEndDate() throws Exception {
        test("2016-02-06", "   ", LocalDate.parse("2016-02-06"), null);
    }

    @Test(expected = BadRequestException.class)
    public void malformedEndDate() throws Exception {
        test("2016-02-06", "also not a date", LocalDate.parse("2016-02-06"), null);
    }

    @Test(expected = BadRequestException.class)
    public void timestampAsEndDate() throws Exception {
        test("2016-02-06", "2016-02-08T23:45-0800", LocalDate.parse("2016-02-06"), null);
    }

    @Test
    public void bothDatesSpecified() throws Exception {
        test("2016-02-06", "2016-02-08", LocalDate.parse("2016-02-06"), LocalDate.parse("2016-02-08"));
    }

    private static void test(String startDateStr, String endDateStr, LocalDate expectedStartDate,
            LocalDate expectedEndDate) throws Exception {
        // Spy controller. Mock session.
        
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(DUMMY_HEALTH_CODE).build();

        UserSession session = new UserSession(participant);

        MpowerVisualizationController controller = spy(new MpowerVisualizationController());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();

        // mock service - For rapid development and to avoid unnecessary coupling, just mock the visualization with a
        // dummy string. Check if that dummy string is propagated through the controller.
        MpowerVisualizationService mockSvc = mock(MpowerVisualizationService.class);
        JsonNode mockViz = new TextNode("mock visualization");
        when(mockSvc.getVisualization(DUMMY_HEALTH_CODE, expectedStartDate, expectedEndDate)).thenReturn(mockViz);

        controller.setMpowerVisualizationService(mockSvc);

        // execute and validate
        Result result = controller.getVisualization(startDateStr, endDateStr);
        assertEquals(200, result.status());

        String resultStr = Helpers.contentAsString(result);
        JsonNode resultNode = BridgeObjectMapper.get().readTree(resultStr);
        assertEquals("mock visualization", resultNode.textValue());
    }

    @Test
    public void testWrite() throws Exception {
        // Spy controller. Mock session.
        MpowerVisualizationController controller = spy(new MpowerVisualizationController());
        doReturn(new UserSession()).when(controller).getAuthenticatedSession(Roles.WORKER);

        // Mock service.
        MpowerVisualizationService mockSvc = mock(MpowerVisualizationService.class);
        controller.setMpowerVisualizationService(mockSvc);

        // Mock request JSON. For simplicity of test, just use the visualization field and just use a string. JSON
        // serialization is already tested in DynamoMpowerVisualizationTest, and validation is handled at the service
        // layer
        String requestJsonText = "{\n" +
                "   \"visualization\":\"strictly for controller test\"\n" +
                "}";
        TestUtils.mockPlayContextWithJson(requestJsonText);

        // execute
        Result result = controller.writeVisualization();
        assertEquals(201, result.status());

        // Verify call to service, and that the visualization passed in has the dummy string we created.
        ArgumentCaptor<MpowerVisualization> vizCaptor = ArgumentCaptor.forClass(MpowerVisualization.class);
        verify(mockSvc).writeVisualization(vizCaptor.capture());

        MpowerVisualization viz = vizCaptor.getValue();
        assertEquals("strictly for controller test", viz.getVisualization().textValue());

        // Verify we get a Worker session.
        verify(controller).getAuthenticatedSession(Roles.WORKER);
    }
}
