package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import play.mvc.Result;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.sms.SmsOptOutSettings;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.SmsService;
import org.sagebionetworks.bridge.services.StudyService;

public class SmsControllerTest {
    private static final String MESSAGE_ID = "my-message-id";
    private static final String PHONE_NUMBER = "+12065550123";
    private static final String USER_ID = "test-user";

    private static final Study DUMMY_STUDY;
    static {
        DUMMY_STUDY = Study.create();
        DUMMY_STUDY.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
    }

    private SmsController controller;
    private SmsService mockSmsService;

    @Before
    public void before() {
        // Mock study service.
        StudyService mockStudyService = mock(StudyService.class);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(DUMMY_STUDY);

        // Mock SMS service.
        mockSmsService = mock(SmsService.class);

        // Set up controller.
        controller = spy(new SmsController());
        controller.setSmsService(mockSmsService);
        controller.setStudyService(mockStudyService);

        // Mock get session.
        UserSession session = new UserSession();
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        doReturn(session).when(controller).getAuthenticatedSession(Roles.ADMIN);
    }

    @Test
    public void getMostRecentMessage() throws Exception {
        // Setup test. This method is a passthrough for SmsMessage, so just verify one attribute.
        SmsMessage svcOutput = SmsMessage.create();
        svcOutput.setMessageId(MESSAGE_ID);
        when(mockSmsService.getMostRecentMessage(DUMMY_STUDY, USER_ID)).thenReturn(svcOutput);

        // Execute and verify.
        Result result = controller.getMostRecentMessage(USER_ID);
        assertEquals(200, result.status());

        SmsMessage controllerOutput = TestUtils.getResponsePayload(result, SmsMessage.class);
        assertEquals(MESSAGE_ID, controllerOutput.getMessageId());
    }

    @Test
    public void getOptOutSettings() throws Exception {
        // Setup test. This method is a passthrough for SmsOptOutSettings, so just verify one attribute.
        SmsOptOutSettings svcOutput = SmsOptOutSettings.create();
        svcOutput.setPhoneNumber(PHONE_NUMBER);
        when(mockSmsService.getOptOutSettings(DUMMY_STUDY, USER_ID)).thenReturn(svcOutput);

        // Execute and verify.
        Result result = controller.getOptOutSettings(USER_ID);
        assertEquals(200, result.status());

        SmsOptOutSettings controllerOutput = TestUtils.getResponsePayload(result, SmsOptOutSettings.class);
        assertEquals(PHONE_NUMBER, controllerOutput.getPhoneNumber());
    }

    @Test
    public void setOptOutSettings() throws Exception {
        // Setup test. This method is a passthrough for SmsOptOutSettings, so just verify one attribute.
        SmsOptOutSettings controllerInput = SmsOptOutSettings.create();
        controllerInput.setPhoneNumber(PHONE_NUMBER);
        TestUtils.mockPlayContextWithJson(controllerInput);

        // Execute and verify.
        Result result = controller.setOptOutSettings(USER_ID);
        TestUtils.assertResult(result, 200, "SMS opt-out settings updated");

        // Verify service call.
        ArgumentCaptor<SmsOptOutSettings> svcInputCaptor = ArgumentCaptor.forClass(SmsOptOutSettings.class);
        verify(mockSmsService).setOptOutSettings(same(DUMMY_STUDY), eq(USER_ID), svcInputCaptor.capture());

        SmsOptOutSettings svcInput = svcInputCaptor.getValue();
        assertEquals(PHONE_NUMBER, svcInput.getPhoneNumber());
    }
}
