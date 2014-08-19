package org.sagebionetworks.bridge.services;

import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.User;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static play.test.Helpers.*;

public class ConsentServiceImplTest {
    private Client stormpathClient;
    private ConsentServiceImpl consentService;
    private UserConsentDao userConsentDao;
    private User user;
    private Study study;

    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        stormpathClient = mock(Client.class);
         
        Account account = mock(Account.class);
        CustomData customData = mock(CustomData.class);
        when(account.getCustomData()).thenReturn(customData);
        
        when(stormpathClient.getResource(anyString(), any(Class.class))).thenReturn(account);
        
        user = new User();
        user.setUsername("user");
        user.setEmail("a@sagebase.org");
        user.setFirstName("first");
        user.setLastName("last");
        user.setStudyKey("1234");
        
        study = mock(Study.class);
        userConsentDao = mock(UserConsentDao.class);
        StudyConsentDao studyConsentDao = mock(StudyConsentDao.class);

        consentService = new ConsentServiceImpl();
        consentService.setStormpathClient(stormpathClient);
        consentService.setUserConsentDao(userConsentDao);
        consentService.setStudyConsentDao(studyConsentDao);
    }

    @Test
    public void withdrawRemovesConsent() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                /* TODO: Re-enable these when userConsentDao is in use again.
                ArgumentCaptor<String> healthCode = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<StudyConsent> studyConsent = ArgumentCaptor.forClass(StudyConsent.class);
                */
                user.setConsent(true);
                consentService.withdrawConsent(user, study);

                // TODO: The user should be returned if the user is manipulated.
                assertEquals(user.doesConsent(), false);
                //verify(userConsentDao).withdrawConsent(healthCode.capture(), studyConsent.capture());
            }

        });
    }
}
