package controllers;

import static org.apache.commons.httpclient.HttpStatus.SC_CREATED;
import static org.apache.commons.httpclient.HttpStatus.SC_FORBIDDEN;
import static org.apache.commons.httpclient.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.STUDYCONSENT_ACTIVE_URL;
import static org.sagebionetworks.bridge.TestConstants.STUDYCONSENT_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyConsentControllerTest {

    private ObjectMapper mapper = new ObjectMapper();

    public StudyConsentControllerTest() {
        mapper.setSerializationInclusion(Include.NON_NULL);
    }

    @Resource
    private TestUserAdminHelper helper;
    
    private TestUser adminSession;
    
    private TestUser userSession;

    @Before
    public void before() {
        adminSession = helper.createUser(StudyConsentControllerTest.class, BridgeConstants.ADMIN_GROUP);
        userSession = helper.createUser(StudyConsentControllerTest.class);
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
                ObjectNode consent = JsonNodeFactory.instance.objectNode();
                consent.put("minAge", 17);
                consent.put("path", "conf/email-templates/teststudy-consent.html");

                Response addConsentFail = TestUtils.getURL(userSession.getSessionToken(), STUDYCONSENT_URL)
                        .post(consent.toString()).get(TIMEOUT);
                assertEquals("Must be admin to access consent.", SC_FORBIDDEN, addConsentFail.getStatus());

                Response addConsent = TestUtils.getURL(adminSession.getSessionToken(), STUDYCONSENT_URL)
                        .post(consent.toString()).get(TIMEOUT);
                assertEquals("Successfully add consent.", SC_CREATED, addConsent.getStatus());

                // Get timeout to access this consent later.
                String createdOn = addConsent.asJson().get("createdOn").asText();

                Response setActive = TestUtils
                        .getURL(adminSession.getSessionToken(), STUDYCONSENT_ACTIVE_URL + "/" + createdOn).post("")
                        .get(TIMEOUT);
                assertEquals("Successfully set active consent.", SC_OK, setActive.getStatus());

                Response getActive = TestUtils.getURL(adminSession.getSessionToken(), STUDYCONSENT_ACTIVE_URL).get()
                        .get(TIMEOUT);
                assertEquals("Successfully get active consent.", SC_OK, getActive.getStatus());
                
                // Test the types on the consent
                JsonNode node = getActive.asJson();
                assertEquals("Type is StudyConsent", "StudyConsent", node.get("type").asText());

                Response getAll = TestUtils.getURL(adminSession.getSessionToken(), STUDYCONSENT_URL).get().get(TIMEOUT);
                assertEquals("Successfully get all consents.", SC_OK, getAll.getStatus());
            }
        });
    }

}
