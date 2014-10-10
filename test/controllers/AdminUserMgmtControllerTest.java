package controllers;

import static org.apache.commons.httpclient.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_KEY;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static org.sagebionetworks.bridge.TestConstants.USER_URL;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.TestUtils.FailableRunnable;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent2;
import org.sagebionetworks.bridge.models.UserSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AdminUserMgmtControllerTest {

    @Resource
    private TestUserAdminHelper helper;

    private UserSession session;

    @BeforeClass
    public static void initialSetUp() {
        DynamoTestUtil.clearTable(DynamoUserConsent2.class);
    }

    @AfterClass
    public static void finalCleanUp() {
        DynamoTestUtil.clearTable(DynamoUserConsent2.class);
    }

    @Before
    public void before() {
        session = helper.createUser(Lists.newArrayList(BridgeConstants.ADMIN_GROUP));
    }

    @After
    public void after() {
        helper.deleteUser(session);
    }

    @Test
    public void canCreateAndDeleteUser() {
        running(testServer(3333), new FailableRunnable() {

            @Override
            public void testCode() throws Exception {
                String email = "testtestingggggg@sagebridge.org";

                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put("email", email);
                node.put("username", "unique_usernameeee");
                node.put("password", "1234");
                node.put("consent", true);

                Response response = TestUtils.getURL(session.getSessionToken(), USER_URL)
                        .setHeader("Bridge-Host", TEST_STUDY_KEY)
                        .post(node)
                        .get(TIMEOUT);
                assertEquals("Response status is OK.", SC_OK, response.getStatus());

                Map<String,String> queryParams = new HashMap<String,String>();
                queryParams.put("email", email);

                response = TestUtils.getURL(session.getSessionToken(), USER_URL, queryParams)
                        .delete()
                        .get(TIMEOUT);
                assertEquals("Response status is OK.", SC_OK, response.getStatus());
            }

        });
    }
}
