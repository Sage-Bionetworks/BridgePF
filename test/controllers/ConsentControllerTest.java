package controllers;

import static org.apache.commons.httpclient.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.commons.httpclient.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.CONSENT_URL;
import static org.sagebionetworks.bridge.TestConstants.RESUME_URL;
import static org.sagebionetworks.bridge.TestConstants.SUSPEND_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.Arrays;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.DateConverter;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.UserSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ConsentControllerTest {

    private long timestamp;
    private UserSession session;

    @Resource
    private TestUserAdminHelper helper;

    @Resource
    private StudyConsentDao studyConsentDao;

    @Before
    public void before() {
        String[] roles = { "user" };
        session = helper.createUserWithoutConsent(helper.getTestUser(), Arrays.asList(roles));

        // TODO need to remove the study consent dao - ideally this information is already there, and we don't need to
        // create it.
        StudyConsent consent = studyConsentDao.addConsent(helper.getStudy().getKey(), "fake-path", helper.getStudy()
                .getMinAge());
        studyConsentDao.setActive(consent, true);
        timestamp = consent.getCreatedOn();
    }

    @After
    public void after() {
        helper.deleteUser(session.getUser());
        studyConsentDao.deleteConsent(helper.getStudy().getKey(), timestamp);
    }

    @Test
    public void test() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                String name = "John Smith";
                String birthdate = DateConverter.getCurrentISODateTime();
                boolean sendEmail = false;
                String consentSignature = "{\"name\":\"" + name + "" +
                		"\",\"birthdate\":\"" + birthdate + 
                		"\",\"sendEmail\":" + sendEmail + "}";

                // Helper's user is not consented, so we need to give consent.
                Response giveConsentSuccess = TestUtils.getURL(session.getSessionToken(), CONSENT_URL)
                                                .post(consentSignature)
                                                .get(TIMEOUT);
                assertEquals("Give consent succeeds with 200", giveConsentSuccess.getStatus(), SC_OK);
                
                String sessionToken = giveConsentSuccess.getHeader(BridgeConstants.SESSION_TOKEN_HEADER);
                System.out.println(sessionToken);

                // Helper's user is already consented, so consenting again should fail.
                Response giveConsentFail = TestUtils.getURL(sessionToken, CONSENT_URL)
                                                .post("")
                                                .get(TIMEOUT);
                assertEquals("give Consent fails with 500", giveConsentFail.getStatus(), SC_INTERNAL_SERVER_ERROR);

                // Consenting turns data sharing on by default, so check that we can suspend sharing.
                Response suspendDataSharing = TestUtils.getURL(sessionToken, SUSPEND_URL)
                                                .post("")
                                                .get(TIMEOUT);
                assertEquals("suspendDataSharing succeeds with 200", suspendDataSharing.getStatus(), SC_OK);

                // We've suspended data sharing, now check to see if we can resume data sharing.
                Response resumeDataSharing = TestUtils.getURL(sessionToken, RESUME_URL)
                                                .post("")
                                                .get(TIMEOUT);
                assertEquals("resumeDataSharing succeeds with 200", resumeDataSharing.getStatus(), SC_OK);

                // Resume data sharing should be idempotent.
                resumeDataSharing = TestUtils.getURL(sessionToken, RESUME_URL)
                                                .post("")
                                                .get(TIMEOUT);
                assertEquals("resumeDataSharing succeeds with 200", resumeDataSharing.getStatus(), SC_OK);

            }

        });
    }
}
