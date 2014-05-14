package controllers;

import static org.fest.assertions.Assertions.assertThat;

import static play.mvc.Http.Status.OK;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.Date;

import org.apache.commons.httpclient.HttpStatus;
import org.junit.After;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.libs.WS.Response;
import static org.sagebionetworks.bridge.TestConstants.*;

public class HealthDataControllerTest {

    private JsonNode getTestRecord(String caller) {
        return getTestRecord(caller, 1399666566890L);
    }
    
    private JsonNode getTestRecord(String caller, long date) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("startDate", date);
        node.put("endDate", date);
        
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("systolic", 120L);
        data.put("diastolic", 80L);
        data.put("caller", caller);
        node.put("data", data);
        return node;
    }
    
    private String retrieveNewId(Response response) {
        JsonNode body = response.asJson();
        JsonNode payload = body.get("payload");
        String id = payload.get("id").asText();
        return id;
    }
    
    @After
    public void deleteAllExistingRecords() throws Exception {
        TestUtils.deleteAllHealthData();
    }
    
    @Test
    public void appendHealthData() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = TestUtils.signIn();
                JsonNode node = getTestRecord("appendHealthData");
                
                Response response = TestUtils.getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(OK);
                
                String id = retrieveNewId(response);
                assertThat(id).isNotEmpty();
                
                TestUtils.signOut();
            }
        });
    }

    @Test
    public void getAllHealthData() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = TestUtils.signIn();
                
                // Create a couple of throwaway records
                JsonNode rec1 = getTestRecord("getAllHealthData 1");
                TestUtils.getURL(sessionToken, TRACKER_URL).post(rec1).get(TIMEOUT);
                
                JsonNode rec2 = getTestRecord("getAllHealthData 2");
                TestUtils.getURL(sessionToken, TRACKER_URL).post(rec2).get(TIMEOUT);
                
                JsonNode rec3 = getTestRecord("getAllHealthData 3");
                TestUtils.getURL(sessionToken, TRACKER_URL).post(rec3).get(TIMEOUT);
                
                // Now you should get back at least 2 records
                Response response = TestUtils.getURL(sessionToken, TRACKER_URL).get().get(TIMEOUT);
                
                JsonNode body = response.asJson();
                ArrayNode array = (ArrayNode)body.get("payload");
                assertThat(array.size()).isEqualTo(3);
                
                TestUtils.signOut();
            }
        });
    }
    
    @Test
    public void getHealthDataByDateRange() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = TestUtils.signIn();
                
                long threeDays = (1000L * 60L * 60L * 24L *3L);
                
                long thousandDaysAgo = new Date().getTime() - (1000*60*60*24*1000);
                long time1 = thousandDaysAgo + threeDays;
                long time2 = thousandDaysAgo + threeDays*2;
                long time3 = thousandDaysAgo + threeDays*3;
                long time4 = thousandDaysAgo + threeDays*4;

                ObjectNode node = (ObjectNode)getTestRecord("getHealthDataByDateRange: time1", time1);
                Response response = TestUtils.getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);

                node = (ObjectNode)getTestRecord("getHealthDataByDateRange: time2", time2);
                response = TestUtils.getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                
                node = (ObjectNode)getTestRecord("getHealthDataByDateRange: time3", time3);
                response = TestUtils.getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                
                node = (ObjectNode)getTestRecord("getHealthDataByDateRange: time4", time4);
                response = TestUtils.getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                
                String queryPath = String.format("/%s/%s", Long.toString(time1), Long.toString(time3));
                System.out.println(TRACKER_URL + queryPath);
                response = TestUtils.getURL(sessionToken, TRACKER_URL + queryPath).get().get(TIMEOUT);
                
                JsonNode body = response.asJson();
                ArrayNode array = (ArrayNode)body.get("payload");
                assertThat(array.size()).isEqualTo(3);
                
                TestUtils.signOut();
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
                String sessionToken = TestUtils.signIn();

                JsonNode node = getTestRecord("updateHealthDataRecord");
                Response response = TestUtils.getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);

                // Get the id and set it on the object
                String id = retrieveNewId(response);
                
                // Change some values, add the ID
                ObjectNode onode = (ObjectNode)node.get("data");
                onode.put("systolic", 200L);
                ((ObjectNode)node).put("recordId", id);

                // Save it (update)
                response = TestUtils.getURL(sessionToken, RECORD_URL + id).post(node).get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);

                // Get it and verify that it was persisted.
                response = TestUtils.getURL(sessionToken, RECORD_URL + id).get().get(TIMEOUT);
                JsonNode body = response.asJson();
                JsonNode payload = body.get("payload");
                long valueSaved = payload.get("data").get("systolic").asLong();
                assertThat(valueSaved).isEqualTo(200L);
                
                TestUtils.signOut();
            }
        });
    }
    
    @Test
    public void deleteHealthDataRecord() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = TestUtils.signIn();
                
                JsonNode node = getTestRecord("deleteHealthDataRecord");
                
                Response response = TestUtils.getURL(sessionToken, TRACKER_URL).post(node).get(TIMEOUT);
                
                String id = retrieveNewId(response);
                
                response = TestUtils.getURL(sessionToken, RECORD_URL + id).delete().get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
                
                // Now this should generate a not found
                response = TestUtils.getURL(sessionToken, RECORD_URL + id).get().get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NOT_FOUND);
                
                TestUtils.signOut();
            }
        });
    }
}
