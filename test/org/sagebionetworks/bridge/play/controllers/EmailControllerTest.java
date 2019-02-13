package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.play.controllers.EmailController;
import org.sagebionetworks.bridge.services.StudyService;

import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

import java.util.Map;

import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
public class EmailControllerTest {

    private static final String EMAIL = "email";
    private static final String DATA_BRACKET_EMAIL = "data[email]";
    private static final String HEALTH_CODE = "healthCode";
    private static final String UNSUBSCRIBE_TOKEN = "unsubscribeToken";
    private static final String TOKEN = "token";
    private static final String STUDY2 = "study";
    private static final String API = TestConstants.TEST_STUDY_IDENTIFIER;
    private static final String EMAIL_ADDRESS = "bridge-testing@sagebase.org";
    private static final AccountId ACCOUNT_ID = AccountId.forEmail(TestConstants.TEST_STUDY_IDENTIFIER, EMAIL_ADDRESS);

    @Mock
    private StudyService studyService;

    @Mock
    private AccountDao accountDao;
    
    @Mock
    private Account account;
    
    @Spy
    private EmailController controller;

    private Study study;
    
    @Before
    public void before() {
        BridgeConfig config = mock(BridgeConfig.class);
        when(config.getEmailUnsubscribeToken()).thenReturn(UNSUBSCRIBE_TOKEN);
        
        controller.setAccountDao(accountDao);
        controller.setStudyService(studyService);
        controller.setBridgeConfig(config);
        when(accountDao.getHealthCodeForAccount(ACCOUNT_ID)).thenReturn(HEALTH_CODE);
        
        TestUtils.mockEditAccount(accountDao, account);
        
        study = TestUtils.getValidStudy(EmailControllerTest.class);
        study.setIdentifier(API);
        
        when(studyService.getStudy(API)).thenReturn(study);
    }

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

    @Test
    public void fromQueryParams() throws Exception {
        mockContext(map(DATA_BRACKET_EMAIL, EMAIL_ADDRESS, STUDY2, API, TOKEN, UNSUBSCRIBE_TOKEN), null);

        controller.unsubscribeFromEmail();
        
        verify(account).setNotifyByEmail(false);
    }

    @Test
    public void fromFormPost() throws Exception {
        mockContext(null, map(DATA_BRACKET_EMAIL, EMAIL_ADDRESS, STUDY2, API, TOKEN, UNSUBSCRIBE_TOKEN));

        controller.unsubscribeFromEmail();

        verify(account).setNotifyByEmail(false);
    }

    @Test
    public void fromFormPostWithEmail() throws Exception {
        mockContext(null, map(EMAIL, EMAIL_ADDRESS, STUDY2, API, TOKEN, UNSUBSCRIBE_TOKEN));

        controller.unsubscribeFromEmail();

        verify(account).setNotifyByEmail(false);
    }
    
    @Test
    public void mixed() throws Exception {
        // study and token from query params, like in a real use case. email from form post
        mockContext(map(STUDY2, API, TOKEN, UNSUBSCRIBE_TOKEN), map(DATA_BRACKET_EMAIL, EMAIL_ADDRESS));

        controller.unsubscribeFromEmail();

        verify(account).setNotifyByEmail(false);
    }

    @Test
    public void noStudyThrowsException() throws Exception {
        mockContext(map(DATA_BRACKET_EMAIL, EMAIL_ADDRESS, TOKEN, UNSUBSCRIBE_TOKEN), null);

        Result result = controller.unsubscribeFromEmail();
        assertEquals(200, result.status());
        assertEquals("Study not found.", Helpers.contentAsString(result));
    }

    @Test
    public void noEmailThrowsException() throws Exception {
        mockContext(map(STUDY2, API, TOKEN, UNSUBSCRIBE_TOKEN), null);

        Result result = controller.unsubscribeFromEmail();
        assertEquals(200, result.status());
        assertEquals("Email not found.", Helpers.contentAsString(result));
    }

    @Test
    public void noAccountThrowsException() throws Exception {
        mockContext(map(DATA_BRACKET_EMAIL, EMAIL_ADDRESS, STUDY2, API, TOKEN, UNSUBSCRIBE_TOKEN), null);
        doReturn(null).when(accountDao).getHealthCodeForAccount(ACCOUNT_ID);

        Result result = controller.unsubscribeFromEmail();
        assertEquals(200, result.status());
        assertEquals("Email not found.", Helpers.contentAsString(result));
    }

    @Test
    public void missingTokenThrowsException() throws Exception {
        mockContext(map(DATA_BRACKET_EMAIL, EMAIL_ADDRESS, STUDY2, API), null);

        Result result = controller.unsubscribeFromEmail();
        assertEquals(200, result.status());
        assertEquals("No authentication token provided.", Helpers.contentAsString(result));
    }
}
