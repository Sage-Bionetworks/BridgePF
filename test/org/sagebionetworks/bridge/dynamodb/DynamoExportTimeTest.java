package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class DynamoExportTimeTest {
    private static final String TEST_DATE_TIME = "2016-05-09T00:00:00.000-0700";

    private static final String STUDY_ID = "test-study-id";
    private static final Long LAST_EXPORT_DATE_TIME = DateTime.parse(TEST_DATE_TIME).getMillis();

    @Test
    public void testJson() throws Exception {
        // can serialize
        final DynamoExportTime dynamoExportTime = new DynamoExportTime(STUDY_ID, LAST_EXPORT_DATE_TIME);

        final String json = BridgeObjectMapper.get().writeValueAsString(dynamoExportTime);
        final JsonNode node = BridgeObjectMapper.get().readTree(json);
        assertEqualsAndNotNull(dynamoExportTime.getStudyId(), node.get("studyId").textValue());
        assertEqualsAndNotNull(dynamoExportTime.getLastExportDateTime(), node.get("lastExportDateTime").asLong());

        // can de-serialize
        String testJson = "{\"studyId\":\"" + STUDY_ID + "\",\"lastExportDateTime\":\"" + LAST_EXPORT_DATE_TIME + "\"}";
        DynamoExportTime retExportTime = BridgeObjectMapper.get().readValue(testJson, DynamoExportTime.class);

        assertEqualsAndNotNull(dynamoExportTime.getStudyId(), retExportTime.getStudyId());
        assertEqualsAndNotNull(dynamoExportTime.getLastExportDateTime(), retExportTime.getLastExportDateTime());
    }

    private void assertEqualsAndNotNull(Object expected, Object actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected, actual);
    }
}
