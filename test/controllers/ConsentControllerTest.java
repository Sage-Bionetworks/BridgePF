package controllers;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.CONSENT_URL;
import static org.sagebionetworks.bridge.TestConstants.RESUME_URL;
import static org.sagebionetworks.bridge.TestConstants.SUSPEND_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static play.mvc.Http.Status.INTERNAL_SERVER_ERROR;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ConsentControllerTest {

    private long timestamp;

    @Resource
    private TestUserAdminHelper helper;

    @Resource
    private StudyConsentDao studyConsentDao;

    @Before
    public void before() {
        helper.createOneUser();

        // TODO need to remove the study consent dao - ideally this information is already there, and we don't need to
        // create it.
        StudyConsent consent = studyConsentDao.addConsent(helper.getStudy().getKey(), "fake-path", helper.getStudy()
                .getMinAge());
        studyConsentDao.setActive(consent, true);
        timestamp = consent.getCreatedOn();
    }

    @After
    public void after() {
        helper.deleteOneUser();
        studyConsentDao.deleteConsent(helper.getStudy().getKey(), timestamp);
    }

    @Test
    public void test() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                // Helper's user is already consented, so consenting again should fail.
                Response giveConsentFail = TestUtils.getURL(helper.getUserSessionToken(), CONSENT_URL).post("")
                        .get(TIMEOUT);
                assertEquals("give Consent fails with 500", giveConsentFail.getStatus(), INTERNAL_SERVER_ERROR);

                Response resumeDataSharing = TestUtils.getURL(helper.getUserSessionToken(), RESUME_URL).post("")
                        .get(TIMEOUT);
                assertEquals("resumeDataSharing succeeds with 200", resumeDataSharing.getStatus(), OK);

                Response suspendDataSharing = TestUtils.getURL(helper.getUserSessionToken(), SUSPEND_URL).post("")
                        .get(TIMEOUT);
                assertEquals("suspendDataSharing succeeds with 200", suspendDataSharing.getStatus(), OK);

            }

        });
    }
}
