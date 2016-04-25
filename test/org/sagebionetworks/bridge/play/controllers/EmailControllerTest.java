package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.mockito.Mockito.verify;

import org.apache.http.HttpStatus;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.play.controllers.EmailController;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.StudyService;

import play.mvc.Http;

import java.util.Map;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Maps;

public class EmailControllerTest {

    private static final String EMAIL = "bridge-testing@sagebase.org";

    private ParticipantOptionsService optionsService;

    private AccountDao accountDao;

    private Study study;

    private Map<String, String[]> map(String... values) {
        Map<String, String[]> map = Maps.newHashMap();
        for (int i = 0; i <= values.length - 2; i += 2) {
            map.put(values[i], new String[] { values[i + 1] });
        }
        return map;
    }

    private void mockContext(Map<String, String[]> queryParamMap, Map<String, String[]> formPostMap) {
        Http.RequestBody body = mock(Http.RequestBody.class);
        when(body.asFormUrlEncoded()).thenReturn(formPostMap);

        Http.Request request = mock(Http.Request.class);
        when(request.queryString()).thenReturn(queryParamMap);
        when(request.body()).thenReturn(body);

        Http.Context context = mock(Http.Context.class);
        when(context.request()).thenReturn(request);

        Http.Context.current.set(context);
    }

    private EmailController createController() {
        optionsService = mock(ParticipantOptionsService.class);

        study = TestUtils.getValidStudy(EmailControllerTest.class);
        study.setIdentifier("api");

        accountDao = mock(AccountDao.class);
        when(accountDao.getHealthCodeForEmail(study, EMAIL)).thenReturn("healthCode");

        StudyService studyService = mock(StudyService.class);
        when(studyService.getStudy("api")).thenReturn(study);
        when(studyService.getStudy((String) null)).thenThrow(new EntityNotFoundException(Study.class));

        BridgeConfig config = mock(BridgeConfig.class);
        when(config.getEmailUnsubscribeToken()).thenReturn("unsubscribeToken");

        EmailController controller = spy(new EmailController());
        controller.setParticipantOptionsService(optionsService);
        controller.setStudyService(studyService);
        controller.setAccountDao(accountDao);
        controller.setBridgeConfig(config);
        return controller;
    }

    @Test
    public void fromQueryParams() throws Exception {
        mockContext(map("data[email]", EMAIL, "study", "api", "token", "unsubscribeToken"), null);

        EmailController controller = createController();
        controller.unsubscribeFromEmail();

        verify(optionsService).setBoolean(study, "healthCode", EMAIL_NOTIFICATIONS, false);
    }

    @Test
    public void fromFormPost() throws Exception {
        mockContext(null, map("data[email]", EMAIL, "study", "api", "token", "unsubscribeToken"));

        EmailController controller = createController();
        controller.unsubscribeFromEmail();

        verify(optionsService).setBoolean(study, "healthCode", EMAIL_NOTIFICATIONS, false);
    }

    @Test
    public void mixed() throws Exception {
        // study and token from query params, like in a real use case. email from form post
        mockContext(map("study", "api", "token", "unsubscribeToken"), map("data[email]", EMAIL));

        EmailController controller = createController();
        controller.unsubscribeFromEmail();

        verify(optionsService).setBoolean(study, "healthCode", EMAIL_NOTIFICATIONS, false);
    }

    @Test
    public void noStudyThrowsException() throws Exception {
        mockContext(map("data[email]", EMAIL, "token", "unsubscribeToken"), null);
        EmailController controller = createController();

        try {
            controller.unsubscribeFromEmail();
            fail("Should have thrown exception.");
        } catch (BadRequestException e) {
            assertEquals("Study not found.", e.getMessage());
        }
    }

    @Test
    public void noEmailThrowsException() throws Exception {
        mockContext(map("study", "api", "token", "unsubscribeToken"), null);
        EmailController controller = createController();

        try {
            controller.unsubscribeFromEmail();
            fail("Should have thrown exception");
        } catch (BadRequestException e) {
            assertEquals("Email not found.", e.getMessage());
        }
    }

    @Test
    public void noAccountThrowsException() throws Exception {
        mockContext(map("data[email]", EMAIL, "study", "api", "token", "unsubscribeToken"), null);
        EmailController controller = createController();
        doReturn(null).when(accountDao).getHealthCodeForEmail(study, EMAIL);

        try {
            controller.unsubscribeFromEmail();
            fail("Should have thrown exception");
        } catch (BadRequestException e) {
            assertEquals("Email not found.", e.getMessage());
        }
    }

    @Test
    public void missingTokenThrowsException() throws Exception {
        mockContext(map("data[email]", EMAIL, "study", "api"), null);
        EmailController controller = createController();

        try {
            controller.unsubscribeFromEmail();
            fail("Should have thrown exception");
        } catch (BridgeServiceException e) {
            assertEquals("No authentication token provided.", e.getMessage());
            assertEquals(HttpStatus.SC_UNAUTHORIZED, e.getStatusCode());
        }
    }
}
