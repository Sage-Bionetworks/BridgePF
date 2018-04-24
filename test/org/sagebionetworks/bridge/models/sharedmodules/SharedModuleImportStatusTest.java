package org.sagebionetworks.bridge.models.sharedmodules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;

public class SharedModuleImportStatusTest {
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 7;
    private static final String SURVEY_GUID = "test-survey-guid";

    private static final String SURVEY_CREATED_ON_STRING = "2017-04-05T20:54:53.625Z";
    private static final long SURVEY_CREATED_ON_MILLIS = DateUtils.convertToMillisFromEpoch(SURVEY_CREATED_ON_STRING);

    @Test(expected = NullPointerException.class)
    public void nullSchemaId() {
        new SharedModuleImportStatus(null, SCHEMA_REV);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptySchemaId() {
        new SharedModuleImportStatus("", SCHEMA_REV);
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankSchemaId() {
        new SharedModuleImportStatus("   ", SCHEMA_REV);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeSchemaRev() {
        new SharedModuleImportStatus(SCHEMA_ID, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroSchemaRev() {
        new SharedModuleImportStatus(SCHEMA_ID, 0);
    }

    @Test
    public void schemaModule() {
        // Test constructor and getters.
        SharedModuleImportStatus status = new SharedModuleImportStatus(SCHEMA_ID, SCHEMA_REV);
        assertEquals(SharedModuleType.SCHEMA, status.getModuleType());
        assertEquals(SCHEMA_ID, status.getSchemaId());
        assertEquals(SCHEMA_REV, status.getSchemaRevision().intValue());
        assertNull(status.getSurveyCreatedOn());
        assertNull(status.getSurveyGuid());

        // Test JSON serialization. We only ever write this to JSON, never read it from JSON, so we only need to test
        // this one way.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(status, JsonNode.class);
        assertEquals(4, jsonNode.size());
        assertEquals("schema", jsonNode.get("moduleType").textValue());
        assertEquals(SCHEMA_ID, jsonNode.get("schemaId").textValue());
        assertEquals(SCHEMA_REV, jsonNode.get("schemaRevision").intValue());
        assertEquals("SharedModuleImportStatus", jsonNode.get("type").textValue());
    }

    @Test(expected = NullPointerException.class)
    public void nullSurveyGuid() {
        new SharedModuleImportStatus(null, SURVEY_CREATED_ON_MILLIS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptySurveyGuid() {
        new SharedModuleImportStatus("", SURVEY_CREATED_ON_MILLIS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankSurveyGuid() {
        new SharedModuleImportStatus("   ", SURVEY_CREATED_ON_MILLIS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeSurveyCreatedOn() {
        new SharedModuleImportStatus(SURVEY_GUID, -1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroSurveyCreatedOn() {
        new SharedModuleImportStatus(SURVEY_GUID, 0L);
    }

    @Test
    public void surveyModule() {
        // Test constructor and getters.
        SharedModuleImportStatus status = new SharedModuleImportStatus(SURVEY_GUID, SURVEY_CREATED_ON_MILLIS);
        assertEquals(SharedModuleType.SURVEY, status.getModuleType());
        assertNull(status.getSchemaId());
        assertNull(status.getSchemaRevision());
        assertEquals(SURVEY_GUID, status.getSurveyGuid());
        assertEquals(SURVEY_CREATED_ON_MILLIS, status.getSurveyCreatedOn().longValue());

        // Test JSON serialization.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(status, JsonNode.class);
        assertEquals(4, jsonNode.size());
        assertEquals("survey", jsonNode.get("moduleType").textValue());
        assertEquals(SURVEY_CREATED_ON_STRING, jsonNode.get("surveyCreatedOn").textValue());
        assertEquals(SURVEY_GUID, jsonNode.get("surveyGuid").textValue());
        assertEquals("SharedModuleImportStatus", jsonNode.get("type").textValue());
    }
}
