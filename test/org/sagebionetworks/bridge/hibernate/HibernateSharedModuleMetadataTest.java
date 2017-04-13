package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleType;

public class HibernateSharedModuleMetadataTest {
    private static final String MODULE_ID = "test-module";
    private static final String MODULE_NAME = "Test Module";
    private static final String MODULE_NOTES = "These are my notes for my module.";
    private static final String MODULE_OS = "Android";
    private static final Set<String> MODULE_TAGS = ImmutableSet.of("foo", "bar", "baz");
    private static final int MODULE_VERSION = 3;
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 7;
    private static final String SURVEY_GUID = "test-survey-guid";

    private static final String SURVEY_CREATED_ON_STRING = "2017-04-05T20:54:53.625Z";
    private static final long SURVEY_CREATED_ON_MILLIS = DateUtils.convertToMillisFromEpoch(SURVEY_CREATED_ON_STRING);

    @Test
    public void getModuleTypeSchema() {
        SharedModuleMetadata metadata = SharedModuleMetadata.create();
        metadata.setSchemaId(SCHEMA_ID);
        metadata.setSchemaRevision(SCHEMA_REV);
        assertEquals(SharedModuleType.SCHEMA, metadata.getModuleType());
    }

    @Test
    public void getModuleTypeSurvey() {
        SharedModuleMetadata metadata = SharedModuleMetadata.create();
        metadata.setSurveyCreatedOn(SURVEY_CREATED_ON_MILLIS);
        metadata.setSurveyGuid(SURVEY_GUID);
        assertEquals(SharedModuleType.SURVEY, metadata.getModuleType());
    }

    @Test(expected = InvalidEntityException.class)
    public void getModuleTypeNeither() {
        SharedModuleMetadata.create().getModuleType();
    }

    @Test
    public void tags() {
        SharedModuleMetadata metadata = SharedModuleMetadata.create();

        // tags starts out empty
        assertTrue(metadata.getTags().isEmpty());

        // set the tags
        metadata.setTags(ImmutableSet.of("foo", "bar", "baz"));
        assertEquals(ImmutableSet.of("foo", "bar", "baz"), metadata.getTags());

        // Set tag set to null. It should come back as empty.
        metadata.setTags(null);
        assertTrue(metadata.getTags().isEmpty());
    }

    @Test
    public void jsonSerializationWithOptionalFields() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"id\":\"" + MODULE_ID + "\",\n" +
                "   \"licenseRestricted\":true,\n" +
                "   \"name\":\"" + MODULE_NAME + "\",\n" +
                "   \"notes\":\"" + MODULE_NOTES + "\",\n" +
                "   \"os\":\"" + MODULE_OS + "\",\n" +
                "   \"published\":true,\n" +
                "   \"schemaId\":\"" + SCHEMA_ID + "\",\n" +
                "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
                "   \"tags\":[\"foo\", \"bar\", \"baz\"],\n" +
                "   \"version\":" + MODULE_VERSION + "\n" +
                "}";

        // Convert to POJO
        SharedModuleMetadata metadata = BridgeObjectMapper.get().readValue(jsonText, SharedModuleMetadata.class);
        assertEquals(MODULE_ID, metadata.getId());
        assertTrue(metadata.isLicenseRestricted());
        assertEquals(MODULE_NAME, metadata.getName());
        assertEquals(MODULE_NOTES, metadata.getNotes());
        assertEquals(MODULE_OS, metadata.getOs());
        assertTrue(metadata.isPublished());
        assertEquals(SCHEMA_ID, metadata.getSchemaId());
        assertEquals(SCHEMA_REV, metadata.getSchemaRevision().intValue());
        assertEquals(MODULE_TAGS, metadata.getTags());
        assertEquals(MODULE_VERSION, metadata.getVersion());

        // Convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(metadata, JsonNode.class);
        assertEquals(12, jsonNode.size());
        assertEquals(MODULE_ID, jsonNode.get("id").textValue());
        assertTrue(jsonNode.get("licenseRestricted").booleanValue());
        assertEquals(MODULE_NAME, jsonNode.get("name").textValue());
        assertEquals(MODULE_NOTES, jsonNode.get("notes").textValue());
        assertEquals(MODULE_OS, jsonNode.get("os").textValue());
        assertTrue(jsonNode.get("published").booleanValue());
        assertEquals(SCHEMA_ID, jsonNode.get("schemaId").textValue());
        assertEquals(SCHEMA_REV, jsonNode.get("schemaRevision").intValue());
        assertEquals(MODULE_VERSION, jsonNode.get("version").intValue());
        assertEquals("schema", jsonNode.get("moduleType").textValue());
        assertEquals("SharedModuleMetadata", jsonNode.get("type").textValue());

        JsonNode tagsNode = jsonNode.get("tags");
        assertEquals(3, tagsNode.size());
        Set<String> jsonNodeTags = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            String oneTag = tagsNode.get(i).textValue();
            jsonNodeTags.add(oneTag);
        }
        assertEquals(MODULE_TAGS, jsonNodeTags);
    }

    @Test
    public void jsonSerializationWithSurvey() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"id\":\"" + MODULE_ID + "\",\n" +
                "   \"name\":\"" + MODULE_NAME + "\",\n" +
                "   \"surveyCreatedOn\":\"" + SURVEY_CREATED_ON_STRING + "\",\n" +
                "   \"surveyGuid\":\"" + SURVEY_GUID + "\",\n" +
                "   \"version\":" + MODULE_VERSION + "\n" +
                "}";

        // Convert to POJO - Test only the fields we set, so that we don't have exploding tests.
        SharedModuleMetadata metadata = BridgeObjectMapper.get().readValue(jsonText, SharedModuleMetadata.class);
        assertEquals(MODULE_ID, metadata.getId());
        assertEquals(MODULE_NAME, metadata.getName());
        assertEquals(SURVEY_CREATED_ON_MILLIS, metadata.getSurveyCreatedOn().longValue());
        assertEquals(SURVEY_GUID, metadata.getSurveyGuid());
        assertEquals(MODULE_VERSION, metadata.getVersion());

        // Convert back to JSON. licenseRestricted and published default to false. tags defaults to empty set.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(metadata, JsonNode.class);
        assertEquals(10, jsonNode.size());
        assertEquals(MODULE_ID, jsonNode.get("id").textValue());
        assertFalse(jsonNode.get("licenseRestricted").booleanValue());
        assertEquals(MODULE_NAME, jsonNode.get("name").textValue());
        assertFalse(jsonNode.get("published").booleanValue());
        assertEquals(SURVEY_GUID, jsonNode.get("surveyGuid").textValue());
        assertTrue(jsonNode.get("tags").isArray());
        assertEquals(0, jsonNode.get("tags").size());
        assertEquals(MODULE_VERSION, jsonNode.get("version").intValue());
        assertEquals("survey", jsonNode.get("moduleType").textValue());
        assertEquals("SharedModuleMetadata", jsonNode.get("type").textValue());

        String surveyCreatedOnString = jsonNode.get("surveyCreatedOn").textValue();
        assertEquals(SURVEY_CREATED_ON_MILLIS, DateUtils.convertToMillisFromEpoch(surveyCreatedOnString));
    }
}
