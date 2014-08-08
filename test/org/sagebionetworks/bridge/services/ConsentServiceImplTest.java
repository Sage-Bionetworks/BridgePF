package org.sagebionetworks.bridge.services;

import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.TestConstants.UserCredentials;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import com.stormpath.sdk.client.Client;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static play.test.Helpers.*;

public class ConsentServiceImplTest {
    private Client stormpathClient;
    private CacheProvider cache;
    private UserSession session;
    private ConsentServiceImpl consentService;
    private SendMailService sendMailService;
    private UserConsentDao userConsentDao;
    private Study study;

    @Before
    public void before() {
        stormpathClient = StormpathFactory.createStormpathClient();
        UserCredentials a = new UserCredentials("user", "P4ssword", "a@sagebase.org", "first", "last");
        
        session = new UserSession();
        session.setStudyKey("1234");
        session.setSessionToken("sessionToken");
        TestUtils.addUserToSession(a, session, stormpathClient);
        
        cache = mock(CacheProvider.class);
        when(cache.getUserSession(anyString())).thenReturn(session);

        study = mock(Study.class);
        userConsentDao = mock(UserConsentDao.class);
        sendMailService = mock(SendMailViaAmazonService.class);
        StudyConsentDao studyConsentDao = mock(StudyConsentDao.class);

        consentService = new ConsentServiceImpl();
        consentService.setCacheProvider(cache);
        consentService.setStormpathClient(stormpathClient);
        consentService.setSendMailService(sendMailService);
        consentService.setUserConsentDao(userConsentDao);
        consentService.setStudyConsentDao(studyConsentDao);
    }

    @Test
    public void withdrawRemovesConsent() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                ArgumentCaptor<String> healthCode = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<StudyConsent> studyConsent = ArgumentCaptor.forClass(StudyConsent.class);
                
                session.setConsent(true);
                consentService.withdraw(session, study);

                assertEquals(session.doesConsent(), false);
                verify(userConsentDao).withdrawConsent(healthCode.capture(), studyConsent.capture());
            }

        });
    }
    
    @Test
    public void emailCopyDoesEmail() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                ArgumentCaptor<String> recipientEmail = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<ResearchConsent> consent = ArgumentCaptor.forClass(ResearchConsent.class);
                ArgumentCaptor<Study> argStudy = ArgumentCaptor.forClass(Study.class);
                
                session.setConsent(true);
                consentService.emailCopy(session, study);
                verify(sendMailService).sendConsentAgreement(recipientEmail.capture(), consent.capture(), argStudy.capture());
            }
            
        });
    }

}
