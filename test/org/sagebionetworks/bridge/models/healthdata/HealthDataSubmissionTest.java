package org.sagebionetworks.bridge.models.healthdata;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestUtils.assertDatesWithTimeZoneEqual;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class HealthDataSubmissionTest {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final String CREATED_ON_STR = "2017-08-24T14:38:57.340+0900";
    private static final DateTime CREATED_ON = DateTime.parse(CREATED_ON_STR);
    private static final String PHONE_INFO = "Unit Tests";
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 3;

    @Test
    public void serialization() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"createdOn\":\"" + CREATED_ON_STR + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\",\n" +
                "   \"schemaId\":\"" + SCHEMA_ID + "\",\n" +
                "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
                "   \"data\":{\n" +
                "       \"foo\":\"foo-value\",\n" +
                "       \"bar\":42\n" +
                "   }\n" +
                "}";

        // convert to POJO
        HealthDataSubmission healthDataSubmission = BridgeObjectMapper.get().readValue(jsonText,
                HealthDataSubmission.class);
        assertEquals(APP_VERSION, healthDataSubmission.getAppVersion());
        assertDatesWithTimeZoneEqual(CREATED_ON, healthDataSubmission.getCreatedOn());
        assertEquals(PHONE_INFO, healthDataSubmission.getPhoneInfo());
        assertEquals(SCHEMA_ID, healthDataSubmission.getSchemaId());
        assertEquals(SCHEMA_REV, healthDataSubmission.getSchemaRevision());

        JsonNode pojoData = healthDataSubmission.getData();
        assertEquals(2, pojoData.size());
        assertEquals("foo-value", pojoData.get("foo").textValue());
        assertEquals(42, pojoData.get("bar").intValue());

        // convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(healthDataSubmission, JsonNode.class);
        assertEquals(7, jsonNode.size());
        assertEquals(APP_VERSION, jsonNode.get("appVersion").textValue());
        assertDatesWithTimeZoneEqual(CREATED_ON, DateTime.parse(jsonNode.get("createdOn").textValue()));
        assertEquals(pojoData, jsonNode.get("data"));
        assertEquals(PHONE_INFO, jsonNode.get("phoneInfo").textValue());
        assertEquals(SCHEMA_ID, jsonNode.get("schemaId").textValue());
        assertEquals(SCHEMA_REV, jsonNode.get("schemaRevision").intValue());
        assertEquals("HealthDataSubmission", jsonNode.get("type").textValue());
    }
}
