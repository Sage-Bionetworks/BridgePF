package controllers;

import static org.apache.commons.httpclient.HttpStatus.SC_BAD_REQUEST;
import static org.apache.commons.httpclient.HttpStatus.SC_CREATED;
import static org.apache.commons.httpclient.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.CONSENT_URL;
import static org.sagebionetworks.bridge.TestConstants.RESUME_URL;
import static org.sagebionetworks.bridge.TestConstants.SUSPEND_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.UserSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ConsentControllerTest {

    @Resource
    private TestUserAdminHelper helper;

    private UserSession session;
    
    @Before
    public void before() {
        session = helper.createUser("test");
    }

    @After
    public void after() {
        helper.deleteUser(session);
    }
    
    @Test
    public void test() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            @Override
            public void testCode() throws Exception {

                // Helper's user is already consented, so consenting again should fail.
                Response giveConsentFail = TestUtils.getURL(session.getSessionToken(), CONSENT_URL).post("")
                        .get(TIMEOUT);
                assertEquals("give Consent fails with 400", SC_BAD_REQUEST, giveConsentFail.getStatus());

                // Consenting turns data sharing on by default, so check that we can suspend sharing.
                Response suspendDataSharing = TestUtils.getURL(session.getSessionToken(), SUSPEND_URL).post("")
                        .get(TIMEOUT);
                assertEquals("suspendDataSharing succeeds with 200", SC_OK, suspendDataSharing.getStatus());

                // We've suspended data sharing, now check to see if we can resume data sharing.
                Response resumeDataSharing = TestUtils.getURL(session.getSessionToken(), RESUME_URL).post("")
                        .get(TIMEOUT);
                assertEquals("resumeDataSharing succeeds with 200", SC_OK, resumeDataSharing.getStatus());

                // Resume data sharing should be idempotent.
                resumeDataSharing = TestUtils.getURL(session.getSessionToken(), RESUME_URL).post("").get(TIMEOUT);
                assertEquals("resumeDataSharing succeeds with 200", SC_OK, resumeDataSharing.getStatus());

                UserSession session = null;
                try {
                    TestUser user = new TestUser("johnsmith", "johnsmith@sagebridge.org", "password");
                    session = helper.createUser(user.getSignUp(), helper.getTestStudy(), true, false);
                    
                    // Consent new user again
                    ObjectNode node = JsonNodeFactory.instance.objectNode();
                    node.put("name", "John Smith");
                    node.put("birthdate", DateUtils.getISODate((new DateTime()).minusYears(20)));

                    Response giveConsentSuccess = TestUtils.getURL(session.getSessionToken(), CONSENT_URL)
                            .post(node.toString()).get(TIMEOUT);
                    assertEquals("Give consent succeeds with 201", SC_CREATED, giveConsentSuccess.getStatus());
                } finally {
                    helper.deleteUser(session);
                }
            }
        });
    }
}
