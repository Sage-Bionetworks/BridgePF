package org.sagebionetworks.bridge;

import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.*;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.TestConstants.UserCredentials;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import play.libs.WS;
import play.libs.WS.Response;
import play.libs.WS.WSRequestHolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.AccountStore;
import com.stormpath.sdk.directory.Directory;

public class TestUtils {

    public abstract static class FailableRunnable implements Runnable {
        public abstract void testCode() throws Exception;

        @Override
        public void run() {
            try {
                testCode();
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }
    }

    public static String signIn() throws Exception {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(USERNAME, TEST2.USERNAME);
        node.put(PASSWORD, TEST2.PASSWORD);

        Response response = WS.url(TEST_BASE_URL + SIGN_IN_URL).post(node).get(TIMEOUT);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode responseNode = mapper.readTree(response.getBody());
        return responseNode.get("sessionToken").asText();
    }

    public static WSRequestHolder getURL(String sessionToken, String path) {
        return getURL(sessionToken, path, null);
    }
    
    public static WSRequestHolder getURL(String sessionToken, String path, Map<String,String> queryMap) {
        WSRequestHolder request = WS.url(TEST_BASE_URL + path).setHeader(BridgeConstants.SESSION_TOKEN_HEADER, sessionToken);
        if (queryMap != null) {
            for (Map.Entry<String,String> entry : queryMap.entrySet()) {
                request.setQueryParameter(entry.getKey(), entry.getValue());
            }
        }
        return request;
    }

    public static void signOut() {
        WS.url(TEST_BASE_URL + SIGN_OUT_URL).get().get(TIMEOUT);
    }

    public static void deleteAllHealthData() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = signIn();

                Response response = getURL(sessionToken, TRACKER_URL).get().get(TIMEOUT);

                JsonNode body = response.asJson();
                ArrayNode array = (ArrayNode)body;
                if (array.isArray()) {
                    for (int i = 0; i < array.size(); i++) {
                        JsonNode child = array.get(i);
                        String recordId = child.get(RECORD_ID).asText();
                        response = getURL(sessionToken, RECORD_URL + recordId).delete().get(TIMEOUT);
                    }
                }

                response = getURL(sessionToken, TRACKER_URL).get().get(TIMEOUT);
                body = response.asJson();
                array = (ArrayNode)body;

                signOut();
            }
        });
    }
    
    public static UserProfile constructTestUser(UserCredentials cred) {
        UserProfile user = new UserProfile();
        user.setEmail(cred.EMAIL);
        user.setUsername(cred.USERNAME);
        user.setFirstName(cred.FIRSTNAME);
        user.setLastName(cred.LASTNAME);
        user.setStormpathHref("<EMPTY>");
        
        return user;
    }
    
    public static List<String> getUserProfileFieldNames() {
        List<String> fieldNames = new ArrayList<String>();
        fieldNames.add("firstName");
        fieldNames.add("lastName");
        fieldNames.add("username");
        fieldNames.add("email");
        fieldNames.add("stormpathHref");
        
        return fieldNames;
    }
    
    public static void addUserToSession(UserCredentials cred, UserSession session, Client stormpathClient) {
        Application app = StormpathFactory.createStormpathApplication(stormpathClient);

        // If account associated with email already exists, delete it.
        AccountCriteria criteria = Accounts.where(Accounts.email().eqIgnoreCase(cred.EMAIL));
        AccountList accounts = app.getAccounts(criteria);
        for (Account x : accounts) {
            x.delete();
        }

        // Create Account using credentials
        Account account = stormpathClient.instantiate(Account.class);
        account.setEmail(cred.EMAIL);
        account.setPassword(cred.PASSWORD);
        account.setGivenName(cred.FIRSTNAME);
        account.setSurname(cred.LASTNAME);
        AccountStore store = app.getDefaultAccountStore();
        Directory directory = stormpathClient.getResource(store.getHref(), Directory.class);
        directory.createAccount(account);

        // Create UserProfile using credentials and existing account's HREF
        UserProfile user = new UserProfile();
        user.setEmail(cred.EMAIL);
        user.setUsername(cred.USERNAME);
        user.setFirstName(cred.FIRSTNAME);
        user.setLastName(cred.LASTNAME);
        user.setStormpathHref(account.getHref());

        session.setUser(user);
    }
}
