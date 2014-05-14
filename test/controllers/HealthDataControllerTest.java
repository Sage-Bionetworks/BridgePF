package controllers;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.Date;
import java.util.List;

import org.apache.commons.httpclient.HttpStatus;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

import play.libs.WS;
import play.libs.WS.Response;
import play.libs.WS.WSRequestHolder;
import static org.sagebionetworks.bridge.TestConstants.*;

public class HealthDataControllerTest {

    private String signIn() throws Exception {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(USERNAME, "test2");
        node.put(PASSWORD, PASSWORD);

        Response response = WS.url(TEST_URL + SIGN_IN_URL).post(node).get(TIMEOUT);
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode responseNode = mapper.readTree(response.getBody());
        return responseNode.get("payload").get("sessionToken").asText();
    }

    private WSRequestHolder getURL(String sessionToken, String path) {
        return WS.url(TEST_URL + path).setHeader("Bridge-Session", sessionToken);
    }
    
    private void signOut() {
        WS.url(TEST_URL + SIGN_OUT_URL).get().get(TIMEOUT);
    }
    
    private JsonNode getTestRecord() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("startDate", 1399666566890L);
        node.put("endDate", 1399666566890L);
        
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("systolic", 120L);
        data.put("diastolic", 80L);
        node.put("data", data);
        return node;
    }
    
    private String retrieveNewId(Response response) {
        JsonNode body = response.asJson();
        JsonNode payload = body.get("payload");
        String id = payload.get("id").asText();
        return id;
    }
    
    @Before
    public void deleteAllExistingRecords() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = signIn();
                
                Response response = getURL(sessionToken, TRACKER_URL).get().get(TIMEOUT);
                
                JsonNode body = response.asJson();
                ArrayNode array = (ArrayNode)body.get("payload");
                if (array.isArray()) {
                    for (int i=0; i < array.size(); i++) {
                        JsonNode child = array.get(i);
                        String recordId = child.get("recordId").asText();
                        getURL(sessionToken, RECORD_URL + recordId).delete().get(TIMEOUT);
                    }
                }
                
                response = getURL(sessionToken, TRACKER_URL).get().get(TIMEOUT);
                body = response.asJson();
                array = (ArrayNode)body.get("payload");
                
                signOut();
            }
        });
    }
    
    @Test
    public void appendHealthData() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = signIn();
                JsonNode node = getTestRecord();
                
                Response response = getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(OK);
                
                String id = retrieveNewId(response);
                assertThat(id).isNotEmpty();
                
                signOut();
            }

        });
    }

    @Test
    public void getAllHealthData() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = signIn();
                
                // Create a couple of throwaway records
                JsonNode rec1 = getTestRecord();
                getURL(sessionToken, TRACKER_URL).post(rec1).get(TIMEOUT);
                
                JsonNode rec2 = getTestRecord();
                getURL(sessionToken, TRACKER_URL).post(rec2).get(TIMEOUT);
                
                // Now you should get back at least 2 records
                Response response = getURL(sessionToken, TRACKER_URL).get().get(TIMEOUT);
                
                JsonNode body = response.asJson();
                ArrayNode array = (ArrayNode)body.get("payload");
                assertThat(array.size()).isGreaterThan(2);
                
                signOut();
            }
        });
    }
    
    @Ignore
    @Test
    public void getHealthDataByDateRange() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = signIn();
                long period = (1000L * 60L * 3L);
                List<String> ids = Lists.newArrayList();
                
                long now = new Date().getTime() - (1000*60*60*24*10000);
                long time1 = now + period;
                long time2 = now + period*2;
                long time3 = now + period*3;
                long time4 = now + period*4;

                ObjectNode node = (ObjectNode)getTestRecord();
                node.put("startDate", time1);
                node.put("endDate", time1);
                Response response = getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                ids.add(retrieveNewId(response));
                
                node.put("startDate", time2);
                node.put("endDate", time2);
                getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                response = getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                ids.add(retrieveNewId(response));
                
                node.put("startDate", time3);
                node.put("endDate", time3);
                getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                response = getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                ids.add(retrieveNewId(response));
                
                node.put("startDate", time4);
                node.put("endDate", time4);
                getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                response = getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                ids.add(retrieveNewId(response));
                
                // Whew. Now, do a search
                String queryPath = String.format("?startDate=%s&endDate=%s", new Long(time1).toString(), new Long(time3));
                
                response = getURL(sessionToken, TRACKER_URL + queryPath).get().get(TIMEOUT);
                
                JsonNode body = response.asJson();
                ArrayNode array = (ArrayNode)body.get("payload");
                assertThat(array.size()).isEqualTo(3);
                
                signOut();
            }
        });
    }
    
    @Test
    public void getHealthDataByDate() throws Exception {
    }
    
    @Test
    public void updateHealthDataRecord() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = signIn();

                JsonNode node = getTestRecord();
                Response response = getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);

                // Get the id and set it on the object
                String id = retrieveNewId(response);
                
                // Change some values, add the ID
                ObjectNode onode = (ObjectNode)node.get("data");
                onode.put("systolic", 200L);
                ((ObjectNode)node).put("recordId", id);

                // Save it (update)
                response = getURL(sessionToken, RECORD_URL + id).post(node).get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);

                // Get it and verify that it was persisted.
                response = getURL(sessionToken, RECORD_URL + id).get().get(TIMEOUT);
                JsonNode body = response.asJson();
                JsonNode payload = body.get("payload");
                long valueSaved = payload.get("data").get("systolic").asLong();
                assertThat(valueSaved).isEqualTo(200L);
                
                signOut();
            }
        });
    }
    
    @Test
    public void deleteHealthDataRecord() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = signIn();
                
                JsonNode node = getTestRecord();
                
                Response response = getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                
                String id = retrieveNewId(response);
                
                response = getURL(sessionToken, RECORD_URL + id).delete().get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
                
                // Now this should generate a not found
                response = getURL(sessionToken, RECORD_URL + id).get().get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NOT_FOUND);
                
                signOut();
            }
        });
    }
}
