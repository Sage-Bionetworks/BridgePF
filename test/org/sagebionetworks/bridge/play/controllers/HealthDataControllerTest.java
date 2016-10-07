package org.sagebionetworks.bridge.play.controllers;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.RecordExportStatusRequest;
import org.sagebionetworks.bridge.services.HealthDataService;
import play.mvc.Result;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class HealthDataControllerTest {
    private static final String TEST_RECORD_ID = "record-to-update";
    private static final HealthDataRecord.ExporterStatus TEST_STATUS = HealthDataRecord.ExporterStatus.SUCCEEDED;

    private static final String TEST_STATUS_JSON = "{\n" +
            "   \"recordIds\":[\"record-to-update\"],\n" +
            "   \"synapseExporterStatus\":\"SUCCEEDED\"\n" +
            "}";

    ArgumentCaptor<RecordExportStatusRequest> requestArgumentCaptor = ArgumentCaptor.forClass(RecordExportStatusRequest.class);

    @Mock
    private HealthDataService healthDataService;

    @Mock
    private UserSession workerSession;

    @Mock
    private UserSession consentedUserSession;

    @Mock
    private UserSession otherUserSession;

    @Mock
    private UserSession researcherSession;

    @Test
    public void updateRecordsStatus() throws Exception {
        // mock session
        UserSession mockSession = new UserSession();

        // mock request JSON
        TestUtils.mockPlayContextWithJson(TEST_STATUS_JSON);

        when(healthDataService.updateRecordsWithExporterStatus(anyVararg())).thenReturn(Arrays.asList(TEST_RECORD_ID));

        // spy controller
        HealthDataController controller = spy(new HealthDataController());
        controller.setHealthDataService(healthDataService);
        doReturn(mockSession).when(controller).getAuthenticatedSession(anyVararg());

        // create a mock request entity
        RecordExportStatusRequest mockRequest = new RecordExportStatusRequest();
        mockRequest.setRecordIds(Arrays.asList(TEST_RECORD_ID));
        mockRequest.setSynapseExporterStatus(TEST_STATUS);

        // execute and validate
        Result result = controller.updateRecordsStatus();

        // first verify if it calls the service
        verify(healthDataService).updateRecordsWithExporterStatus(anyVararg());
        // then verify if it parse json correctly as a request entity
        verify(healthDataService).updateRecordsWithExporterStatus(requestArgumentCaptor.capture());
        RecordExportStatusRequest capturedRequest = requestArgumentCaptor.getValue();
        assertEquals(TEST_RECORD_ID, capturedRequest.getRecordIds().get(0));
        assertEquals(TEST_STATUS, capturedRequest.getSynapseExporterStatus());

        // finally, verify the return result
        TestUtils.assertResult(result, 200, "Update exporter status to: " + Arrays.asList(TEST_RECORD_ID) + " complete.");
    }
}
