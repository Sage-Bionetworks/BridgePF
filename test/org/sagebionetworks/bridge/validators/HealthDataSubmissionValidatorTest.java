package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.HashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.joda.time.DateTime;
import org.junit.Test;
import org.springframework.validation.MapBindingResult;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;

public class HealthDataSubmissionValidatorTest {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final DateTime CREATED_ON = DateTime.parse("2017-08-24T14:38:57.340+0900");
    private static final JsonNode DATA = BridgeObjectMapper.get().createObjectNode();
    private static final String PHONE_INFO = "Unit Tests";
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 3;
    private static final DateTime SURVEY_CREATED_ON = DateTime.parse("2017-09-07T15:02:56.756+0900");
    private static final String SURVEY_GUID = "test-survey-guid";

    // branch coverage
    @Test
    public void validatorSupportsClass() {
        assertTrue(HealthDataSubmissionValidator.INSTANCE.supports(HealthDataSubmission.class));
    }

    // branch coverage
    @Test
    public void validatorDoesntSupportClass() {
        assertFalse(HealthDataSubmissionValidator.INSTANCE.supports(String.class));
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateNull() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "HealthDataSubmission");
        HealthDataSubmissionValidator.INSTANCE.validate(null, errors);
        assertTrue(errors.hasErrors());
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateWrongClass() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "HealthDataSubmission");
        HealthDataSubmissionValidator.INSTANCE.validate("this is the wrong class", errors);
        assertTrue(errors.hasErrors());
    }

    @Test
    public void validateSchemaCase() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().build();
        Validate.entityThrowingException(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission);
    }

    @Test
    public void validateSurveyCase() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSurvey().build();
        Validate.entityThrowingException(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission);
    }

    @Test
    public void nullAppVersion() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withAppVersion(null).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "appVersion",
                "is required");
    }

    @Test
    public void emptyAppVersion() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withAppVersion("").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "appVersion",
                "is required");
    }

    @Test
    public void blankAppVersion() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withAppVersion("   ").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "appVersion",
                "is required");
    }

    @Test
    public void nullCreatedOn() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withCreatedOn(null).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "createdOn",
                "is required");
    }

    @Test
    public void dataJavaNull() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withData(null).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "data", "is required");
    }

    @Test
    public void dataJsonNull() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withData(NullNode.getInstance()).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "data", "is required");
    }

    @Test
    public void dataArrayNode() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema()
                .withData(BridgeObjectMapper.get().createArrayNode()).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "data",
                "must be an object node");
    }

    @Test
    public void metadataWrongType() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withMetadata(IntNode.valueOf(42))
                .build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "metadata",
                "must be an object node");
    }

    @Test
    public void nullPhoneInfo() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withPhoneInfo(null).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "phoneInfo",
                "is required");
    }

    @Test
    public void emptyPhoneInfo() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withPhoneInfo("").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "phoneInfo",
                "is required");
    }

    @Test
    public void blankPhoneInfo() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withPhoneInfo("   ").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "phoneInfo",
                "is required");
    }

    @Test
    public void neitherSchemaNorSurvey() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithoutSchemaOrSurvey().build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "healthDataSubmission",
                "must have either schemaId/Revision or surveyGuid/CreatedOn but not both");
    }

    @Test
    public void bothSchemaAndSurvey() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithoutSchemaOrSurvey().withSchemaId(SCHEMA_ID)
                .withSchemaRevision(SCHEMA_REV).withSurveyGuid(SURVEY_GUID).withSurveyCreatedOn(SURVEY_CREATED_ON)
                .build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "healthDataSubmission",
                "must have either schemaId/Revision or surveyGuid/CreatedOn but not both");
    }

    @Test
    public void emptySchemaId() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withSchemaId("").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "schemaId",
                "can't be empty or blank");
    }

    @Test
    public void blankSchemaId() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withSchemaId("   ").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "schemaId",
                "can't be empty or blank");
    }

    @Test
    public void zeroSchemaRev() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withSchemaRevision(0).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "schemaRevision",
                "can't be zero or negative");
    }

    @Test
    public void negativeSchemaRev() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSchema().withSchemaRevision(-1).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "schemaRevision",
                "can't be zero or negative");
    }

    @Test
    public void emptySurveyGuid() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSurvey().withSurveyGuid("").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "surveyGuid",
                "can't be empty or blank");
    }

    @Test
    public void blankSurveyGuid() {
        HealthDataSubmission healthDataSubmission = makeValidBuilderWithSurvey().withSurveyGuid("   ").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "surveyGuid",
                "can't be empty or blank");
    }

    private static HealthDataSubmission.Builder makeValidBuilderWithSchema() {
        return makeValidBuilderWithoutSchemaOrSurvey().withSchemaId(SCHEMA_ID).withSchemaRevision(SCHEMA_REV);
    }

    private static HealthDataSubmission.Builder makeValidBuilderWithSurvey() {
        return makeValidBuilderWithoutSchemaOrSurvey().withSurveyGuid(SURVEY_GUID)
                .withSurveyCreatedOn(SURVEY_CREATED_ON);
    }

    private static HealthDataSubmission.Builder makeValidBuilderWithoutSchemaOrSurvey() {
        return new HealthDataSubmission.Builder().withAppVersion(APP_VERSION).withCreatedOn(CREATED_ON).withData(DATA)
                .withPhoneInfo(PHONE_INFO);
    }
}
