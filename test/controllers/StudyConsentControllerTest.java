package controllers;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.STUDYCONSENT_ACTIVE_URL;
import static org.sagebionetworks.bridge.TestConstants.STUDYCONSENT_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static play.mvc.Http.Status.FORBIDDEN;
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
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent;
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent2;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyConsentControllerTest {

    private ObjectMapper mapper = new ObjectMapper();

    public StudyConsentControllerTest() {
        mapper.setSerializationInclusion(Include.NON_NULL);
    }

    @Resource
    private TestUserAdminHelper helper;

    @Before
    public void before() {
        // TODO: Temporary patch. These DynamoDB clean up calls shouldn't be here
        // But the tables are not properly cleaned by tests run before us.
        DynamoTestUtil.clearTable(DynamoUserConsent.class, "name", "birthdate", "give", "studyKey", "consentTimestamp",
                "version");
        DynamoTestUtil.clearTable(DynamoUserConsent2.class, "signedOn", "dataSharing", "name", "birthdate", "studyKey",
                "healthCode", "consentCreatedOn", "version");
        helper.createOneUser();
    }

    @After
    public void after() {
        helper.deleteOneUser();
    }

    @Test
    public void test() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                // Fields are order independent.
                String consent = "{\"minAge\":17,\"path\":\"fake-path\"}";

                Response addConsentFail = TestUtils.getURL(helper.getUserSessionToken(), STUDYCONSENT_URL)
                                                .post(consent)
                                                .get(TIMEOUT);
                assertEquals("Must be admin to access consent.", FORBIDDEN, addConsentFail.getStatus());

                Response addConsent = TestUtils.getURL(helper.getAdminSessionToken(), STUDYCONSENT_URL)
                                            .post(consent)
                                            .get(TIMEOUT);
                assertEquals("Successfully add consent.", OK, addConsent.getStatus());

                // Get timeout to access this consent later.
                String timeout = addConsent.asJson().get("createdOn").asText();

                Response setActive = TestUtils
                        .getURL(helper.getAdminSessionToken(), STUDYCONSENT_ACTIVE_URL + "/" + timeout)
                        .post("")
                        .get(TIMEOUT);
                assertEquals("Successfully set active consent.", OK, setActive.getStatus());

                Response getActive = TestUtils.getURL(helper.getAdminSessionToken(), STUDYCONSENT_ACTIVE_URL)
                                            .get()
                                            .get(TIMEOUT);
                assertEquals("Successfully get active consent.", OK, getActive.getStatus());

                Response getAll = TestUtils.getURL(helper.getAdminSessionToken(), STUDYCONSENT_URL)
                                        .get()
                                        .get(TIMEOUT);
                assertEquals("Successfully get all consents.", OK, getAll.getStatus());

            }
        });
    }

}
