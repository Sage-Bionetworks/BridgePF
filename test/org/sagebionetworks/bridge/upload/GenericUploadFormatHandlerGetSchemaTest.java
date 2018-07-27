package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class GenericUploadFormatHandlerGetSchemaTest {
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 3;
    private static final String SURVEY_CREATED_ON_STRING = "2017-09-07T15:02:56.756Z";
    private static final long SURVEY_CREATED_ON_MILLIS = DateUtils.convertToMillisFromEpoch(SURVEY_CREATED_ON_STRING);
    private static final String SURVEY_GUID = "test-survey-guid";

    // Don't care about params. This is just a passthrough.
    private static final UploadSchema DUMMY_SCHEMA = UploadSchema.create();

    private GenericUploadFormatHandler handler;
    private SurveyService mockSurveyService;

    @Before
    public void setup() {
        UploadSchemaService mockSchemaService = mock(UploadSchemaService.class);
        when(mockSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV)).thenReturn(
                DUMMY_SCHEMA);

        mockSurveyService = mock(SurveyService.class);

        handler = new GenericUploadFormatHandler();
        handler.setSurveyService(mockSurveyService);
        handler.setUploadSchemaService(mockSchemaService);
    }

    @Test
    public void schemaFromSurvey() throws Exception {
        // mock survey service
        Survey survey = Survey.create();
        survey.setIdentifier(SCHEMA_ID);
        survey.setSchemaRevision(SCHEMA_REV);
        when(mockSurveyService.getSurvey(TestConstants.TEST_STUDY,
                new GuidCreatedOnVersionHolderImpl(SURVEY_GUID, SURVEY_CREATED_ON_MILLIS), false, true))
                        .thenReturn(survey);

        // make info.json
        ObjectNode infoJsonNode = BridgeObjectMapper.get().createObjectNode();
        infoJsonNode.put(UploadUtil.FIELD_SURVEY_GUID, SURVEY_GUID);
        infoJsonNode.put(UploadUtil.FIELD_SURVEY_CREATED_ON, SURVEY_CREATED_ON_STRING);

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TestConstants.TEST_STUDY, infoJsonNode);
        assertSame(DUMMY_SCHEMA, retVal);
    }

    @Test
    public void surveyWithoutSchema() throws Exception {
        // mock survey service - A survey always has an identifier, but if it doesn't have a schema, then it doesn't
        // have a schema rev.
        Survey survey = Survey.create();
        survey.setIdentifier(SCHEMA_ID);
        survey.setSchemaRevision(null);
        when(mockSurveyService.getSurvey(TestConstants.TEST_STUDY,
                new GuidCreatedOnVersionHolderImpl(SURVEY_GUID, SURVEY_CREATED_ON_MILLIS), false, true))
                        .thenReturn(survey);

        // make info.json
        ObjectNode infoJsonNode = BridgeObjectMapper.get().createObjectNode();
        infoJsonNode.put(UploadUtil.FIELD_SURVEY_GUID, SURVEY_GUID);
        infoJsonNode.put(UploadUtil.FIELD_SURVEY_CREATED_ON, SURVEY_CREATED_ON_STRING);

        // execute and catch exception
        try {
            handler.getUploadSchema(TestConstants.TEST_STUDY, infoJsonNode);
            fail("expected exception");
        } catch (UploadValidationException ex) {
            assertEquals("Schema not found for survey " + SURVEY_GUID + ":" + SURVEY_CREATED_ON_MILLIS,
                    ex.getMessage());
        }
    }

    @Test
    public void schemaFromIdAndRev() throws Exception {
        // make info.json
        ObjectNode infoJsonNode = BridgeObjectMapper.get().createObjectNode();
        infoJsonNode.put(UploadUtil.FIELD_ITEM, SCHEMA_ID);
        infoJsonNode.put(UploadUtil.FIELD_SCHEMA_REV, SCHEMA_REV);

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TestConstants.TEST_STUDY, infoJsonNode);
        assertSame(DUMMY_SCHEMA, retVal);
    }

    @Test
    public void noSchemaParams() throws Exception {
        // make info.json w/ no params
        ObjectNode infoJsonNode = BridgeObjectMapper.get().createObjectNode();

        // execute and catch exception
        try {
            handler.getUploadSchema(TestConstants.TEST_STUDY, infoJsonNode);
            fail("expected exception");
        } catch (UploadValidationException ex) {
            assertEquals("info.json must contain either item and schemaRevision or surveyGuid and surveyCreatedOn",
                    ex.getMessage());
        }
    }
}
