package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class IosSchemaValidationHandler2GetSchemaTest {
    private static final Map<String, Map<String, Integer>> DEFAULT_SCHEMA_REV_MAP =
            ImmutableMap.<String, Map<String, Integer>>of(TestConstants.TEST_STUDY_IDENTIFIER,
                    ImmutableMap.of("schema-rev-test", 2));

    private static final String TEST_SURVEY_CREATED_ON_STRING = "2015-08-27T13:38:55-07:00";
    private static final long TEST_SURVEY_CREATED_ON_MILLIS = DateTime.parse(TEST_SURVEY_CREATED_ON_STRING)
            .getMillis();

    @Test
    public void survey() throws Exception {
        // mock survey service
        DynamoSurvey survey = new DynamoSurvey();
        survey.setIdentifier("test-survey");
        survey.setSchemaRevision(4);

        SurveyService mockSurveyService = mock(SurveyService.class);
        when(mockSurveyService.getSurvey(
                eq(new GuidCreatedOnVersionHolderImpl("test-guid", TEST_SURVEY_CREATED_ON_MILLIS))))
                .thenReturn(survey);

        // mock upload schema service
        UploadSchema dummySchema = new DynamoUploadSchema();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "test-survey", 4)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setSurveyService(mockSurveyService);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("surveyGuid", "test-guid");
        infoJson.put("surveyCreatedOn", TEST_SURVEY_CREATED_ON_STRING);

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TestConstants.TEST_STUDY, infoJson);
        assertSame(dummySchema, retVal);
    }

    // branch coverage: survey with no identifier
    @Test(expected = UploadValidationException.class)
    public void surveyWithNoIdentifier() throws Exception {
        // mock survey service
        DynamoSurvey survey = new DynamoSurvey();
        survey.setSchemaRevision(4);

        SurveyService mockSurveyService = mock(SurveyService.class);
        when(mockSurveyService.getSurvey(
                eq(new GuidCreatedOnVersionHolderImpl("test-guid", TEST_SURVEY_CREATED_ON_MILLIS))))
                .thenReturn(survey);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setSurveyService(mockSurveyService);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("surveyGuid", "test-guid");
        infoJson.put("surveyCreatedOn", TEST_SURVEY_CREATED_ON_STRING);

        // execute, expected exception
        handler.getUploadSchema(TestConstants.TEST_STUDY, infoJson);
    }

    // branch coverage: survey with no schema rev
    @Test(expected = UploadValidationException.class)
    public void surveyWithNoSchemaRev() throws Exception {
        // mock survey service
        DynamoSurvey survey = new DynamoSurvey();
        survey.setIdentifier("test-survey");

        SurveyService mockSurveyService = mock(SurveyService.class);
        when(mockSurveyService.getSurvey(
                eq(new GuidCreatedOnVersionHolderImpl("test-guid", TEST_SURVEY_CREATED_ON_MILLIS))))
                .thenReturn(survey);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setSurveyService(mockSurveyService);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("surveyGuid", "test-guid");
        infoJson.put("surveyCreatedOn", TEST_SURVEY_CREATED_ON_STRING);

        // execute, expected exception
        handler.getUploadSchema(TestConstants.TEST_STUDY, infoJson);
    }

    @Test
    public void itemWithDefaultRev() throws Exception {
        // mock upload schema service
        UploadSchema dummySchema = new DynamoUploadSchema();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "test-schema", 1)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "test-schema");

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TestConstants.TEST_STUDY, infoJson);
        assertSame(dummySchema, retVal);
    }

    @Test
    public void itemWithLegacyMapRev() throws Exception {
        // mock upload schema service
        UploadSchema dummySchema = new DynamoUploadSchema();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "schema-rev-test", 2)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "schema-rev-test");

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TestConstants.TEST_STUDY, infoJson);
        assertSame(dummySchema, retVal);
    }

    @Test
    public void itemWithRev() throws Exception {
        // mock upload schema service
        UploadSchema dummySchema = new DynamoUploadSchema();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "schema-rev-test", 3)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "schema-rev-test");
        infoJson.put("schemaRevision", 3);

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TestConstants.TEST_STUDY, infoJson);
        assertSame(dummySchema, retVal);
    }

    @Test
    public void fallbackToIdentifier() throws Exception {
        // mock upload schema service
        UploadSchema dummySchema = new DynamoUploadSchema();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, "test-schema", 1)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("identifier", "test-schema");

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TestConstants.TEST_STUDY, infoJson);
        assertSame(dummySchema, retVal);
    }

    // branch coverage: no item or survey
    @Test(expected = UploadValidationException.class)
    public void missingItemOrSurvey() throws Exception {
        new IosSchemaValidationHandler2().getUploadSchema(TestConstants.TEST_STUDY,
                BridgeObjectMapper.get().createObjectNode());
    }
}
