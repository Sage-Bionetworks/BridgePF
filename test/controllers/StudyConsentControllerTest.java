package controllers;

import static org.junit.Assert.*;
import static org.sagebionetworks.bridge.TestConstants.*;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import javax.annotation.Resource;

import org.junit.*;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
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
    TestUserAdminHelper helper;

    @Before
    public void before() {
        helper.createOneUser();
    }

    @After
    public void after() {
        helper.deleteOneUser();
        DynamoTestUtil.clearTable(DynamoStudyConsent1.class, "active", "path", "minAge", "version");
    }

    @Test
    public void test() {
        running(testServer(3333), new TestUtils.FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                DynamoStudyConsent1 consent = new DynamoStudyConsent1();
                consent.setMinAge(17);
                consent.setPath("fake-path");

                Response addConsentFail = TestUtils.getURL(helper.getUserSessionToken(), STUDYCONSENT_URL)
                                            .post(mapper.writeValueAsString(consent))
                                            .get(TIMEOUT);
                assertEquals("Must be admin to access consent.", FORBIDDEN, addConsentFail.getStatus());

                Response addConsent = TestUtils.getURL(helper.getAdminSessionToken(), STUDYCONSENT_URL)
                                        .post(mapper.writeValueAsString(consent))
                                        .get(TIMEOUT);
                assertEquals("Successfully add consent.", OK, addConsent.getStatus());

                // Get timeout to access this consent later.
                String timeout = addConsent.asJson().get("createdOn").asText();

                Response getActive = TestUtils.getURL(helper.getAdminSessionToken(), STUDYCONSENT_ACTIVE_URL)
                                        .get()
                                        .get(TIMEOUT);
                assertEquals("Successfully get active consent.", OK, getActive.getStatus());

                Response setActive = TestUtils.getURL(helper.getAdminSessionToken(), STUDYCONSENT_ACTIVE_URL + "/" + timeout)
                                        .post("")
                                        .get(TIMEOUT);
                assertEquals("Successfully set active consent.", OK, setActive.getStatus());

                Response getAll = TestUtils.getURL(helper.getAdminSessionToken(), STUDYCONSENT_URL)
                                    .get()
                                    .get(TIMEOUT);
                assertEquals("Successfully get all consents.", OK, getAll.getStatus());

            }
        });
    }

}
