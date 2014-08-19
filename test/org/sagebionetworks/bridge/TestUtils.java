package org.sagebionetworks.bridge;

import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.*;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.Map;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import play.libs.WS;
import play.libs.WS.Response;
import play.libs.WS.WSRequestHolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
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

    public static void deleteHealthDataFor(final UserSession session) {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                Response response = getURL(session.getSessionToken(), TRACKER_URL).get().get(TIMEOUT);

                JsonNode body = response.asJson();
                ArrayNode array = (ArrayNode)body;
                if (array.isArray()) {
                    for (int i = 0; i < array.size(); i++) {
                        JsonNode child = array.get(i);
                        String recordId = child.get(RECORD_ID).asText();
                        response = getURL(session.getSessionToken(), RECORD_URL + recordId).delete().get(TIMEOUT);
                    }
                }

                response = getURL(session.getSessionToken(), TRACKER_URL).get().get(TIMEOUT);
                body = response.asJson();
                array = (ArrayNode)body;
            }
        });
    }
    
    public static void addUserToSession(TestUser testUser, UserSession session, Client stormpathClient) {
        Application app = StormpathFactory.createStormpathApplication(stormpathClient);

        // If account associated with email already exists, delete it.
        AccountCriteria criteria = Accounts.where(Accounts.email().eqIgnoreCase(testUser.getEmail()));
        AccountList accounts = app.getAccounts(criteria);
        for (Account x : accounts) {
            x.delete();
        }

        // Create Account using credentials
        Account account = stormpathClient.instantiate(Account.class);
        account.setEmail(testUser.getEmail());
        account.setPassword(testUser.getPassword());
        account.setGivenName("<EMPTY>");
        account.setSurname("<EMPTY>");
        AccountStore store = app.getDefaultAccountStore();
        Directory directory = stormpathClient.getResource(store.getHref(), Directory.class);
        directory.createAccount(account);

        User user = new User(account);

        session.setUser(user);
    }
}
