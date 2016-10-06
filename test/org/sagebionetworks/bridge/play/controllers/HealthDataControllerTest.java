package org.sagebionetworks.bridge.play.controllers;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.UploadService;
import play.mvc.Result;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
public class HealthDataControllerTest {
    private static final String TEST_STATUS_JSON = "{\n" +
            "   \"recordIds\":[\"record-to-update\"],\n" +
            "   \"synapseExporterStatus\":\"SUCCEEDED\"\n" +
            "}";

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

        // spy controller
        HealthDataController controller = spy(new HealthDataController());
        controller.setHealthDataService(healthDataService);
        doReturn(mockSession).when(controller).getAuthenticatedSession(anyVararg());

        // execute and validate
        Result result = controller.setRecordsStatus();
        assertEquals(result.status(), 200);
    }
}
