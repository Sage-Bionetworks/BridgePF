package org.sagebionetworks.bridge.services;

import org.junit.*;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.TestConstants.UserCredentials;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import com.stormpath.sdk.client.Client;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static play.test.Helpers.*;

public class ConsentServiceImplTest {
    private Client stormpathClient;
    private CacheProvider cache;
    private UserSession session;
    private ConsentServiceImpl consentService;
    private Study study;
    private UserConsentDao userConsentDao;
    private StudyConsentDao studyConsentDao;

    @Before
    public void before() {
        stormpathClient = StormpathFactory.createStormpathClient();

        session = new UserSession();
        session.setStudyKey("1234");

        cache = mock(CacheProvider.class);
        when(cache.getUserSession(anyString())).thenReturn(session);

        study = mock(Study.class);
        userConsentDao = mock(UserConsentDao.class);
        studyConsentDao = mock(StudyConsentDao.class);

        consentService = new ConsentServiceImpl();
        consentService.setStormpathClient(stormpathClient);
        consentService.setCacheProvider(cache);
        consentService.setUserConsentDao(userConsentDao);
        consentService.setStudyConsentDao(studyConsentDao);
    }

    @Test
    public void withdrawRemovesConsent() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                UserCredentials a = new UserCredentials("auser", "P4ssword", "a@sagebase.org", "afirstname",
                        "alastname");
                TestUtils.addUserToSession(a, session, stormpathClient);
                session.setConsent(true);
                consentService.withdraw(session, study);

                assertEquals(session.doesConsent(), false);
            }

        });
    }

}
