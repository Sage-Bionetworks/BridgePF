package controllers;

import static org.apache.commons.httpclient.HttpStatus.SC_FORBIDDEN;
import static org.apache.commons.httpclient.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.STUDYCONSENT_ACTIVE_URL;
import static org.sagebionetworks.bridge.TestConstants.STUDYCONSENT_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.UserSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyConsentControllerTest {

    private ObjectMapper mapper = new ObjectMapper();

    public StudyConsentControllerTest() {
        mapper.setSerializationInclusion(Include.NON_NULL);
    }

    @Resource
    private TestUserAdminHelper helper;
    
    private UserSession adminSession;
    
    private UserSession userSession;

    @Before
    public void before() {
        List<String> roles = Lists.newArrayList(BridgeConstants.ADMIN_GROUP);
        // TODO: When you create two users, they need different email/names. Randomize this in the helper 
        // so you don't have to spell this out. 
        adminSession = helper.createUser(new TestUser("admin-user", "admin-user@sagebridge.org", "P4ssword"), roles,
                helper.getStudy(), true, true);
        userSession = helper.createUser(new TestUser("normal-user", "normal-user@sagebridge.org", "P4ssword"), null,
                helper.getStudy(), true, true);
    }

    @After
    public void after() {
        helper.deleteUser(adminSession);
        helper.deleteUser(userSession);
    }

    @Test
    public void test() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                // Fields are order independent.
                String consent = "{\"minAge\":17,\"path\":\"fake-path\"}";

                Response addConsentFail = TestUtils.getURL(userSession.getSessionToken(), STUDYCONSENT_URL)
                                                .post(consent)
                                                .get(TIMEOUT);
                assertEquals("Must be admin to access consent.", SC_FORBIDDEN, addConsentFail.getStatus());

                Response addConsent = TestUtils.getURL(adminSession.getSessionToken(), STUDYCONSENT_URL)
                                            .post(consent)
                                            .get(TIMEOUT);
                assertEquals("Successfully add consent.", SC_OK, addConsent.getStatus());

                // Get timeout to access this consent later.
                String createdOn = addConsent.asJson().get("createdOn").asText();

                Response setActive = TestUtils
                        .getURL(adminSession.getSessionToken(), STUDYCONSENT_ACTIVE_URL + "/" + createdOn)
                        .post("")
                        .get(TIMEOUT);
                assertEquals("Successfully set active consent.", SC_OK, setActive.getStatus());

                Response getActive = TestUtils.getURL(adminSession.getSessionToken(), STUDYCONSENT_ACTIVE_URL)
                                            .get()
                                            .get(TIMEOUT);
                assertEquals("Successfully get active consent.", SC_OK, getActive.getStatus());

                Response getAll = TestUtils.getURL(adminSession.getSessionToken(), STUDYCONSENT_URL)
                                        .get()
                                        .get(TIMEOUT);
                assertEquals("Successfully get all consents.", SC_OK, getAll.getStatus());
            }
        });
    }

}
