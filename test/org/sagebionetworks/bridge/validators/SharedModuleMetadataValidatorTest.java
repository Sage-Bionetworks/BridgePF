package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.springframework.validation.MapBindingResult;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.hibernate.HibernateSharedModuleMetadata;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;

public class SharedModuleMetadataValidatorTest {
    private static final String MODULE_ID = "test-module";
    private static final String MODULE_NAME = "Test Module";
    private static final int MODULE_VERSION = 3;
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 7;
    private static final long SURVEY_CREATED_ON = 1337;
    private static final String SURVEY_GUID = "test-survey-guid";

    // branch coverage
    @Test
    public void validatorSupportsClass() {
        assertTrue(SharedModuleMetadataValidator.INSTANCE.supports(SharedModuleMetadata.class));
    }

    // branch coverage
    @Test
    public void validatorSupportsSubclass() {
        assertTrue(SharedModuleMetadataValidator.INSTANCE.supports(HibernateSharedModuleMetadata.class));
    }

    // branch coverage
    @Test
    public void validatorDoesntSupportsClass() {
        assertFalse(SharedModuleMetadataValidator.INSTANCE.supports(String.class));
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateNull() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "SharedModuleMetadata");
        SharedModuleMetadataValidator.INSTANCE.validate(null, errors);
        assertTrue(errors.hasErrors());
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateWrongClass() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "SharedModuleMetadata");
        SharedModuleMetadataValidator.INSTANCE.validate("this is the wrong class", errors);
        assertTrue(errors.hasErrors());
    }

    @Test
    public void validWithSchema() {
        Validate.entityThrowingException(SharedModuleMetadataValidator.INSTANCE, makeValidMetadataWithSchema());
    }

    @Test
    public void validWithSurvey() {
        Validate.entityThrowingException(SharedModuleMetadataValidator.INSTANCE, makeValidMetadataWithSurvey());
    }

    @Test
    public void validWithOptionalParams() {
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setLicenseRestricted(true);
        metadata.setNotes("These are some notes.");
        metadata.setOs("Android");
        metadata.setPublished(true);
        metadata.setTags(ImmutableSet.of("foo", "bar", "baz"));
        Validate.entityThrowingException(SharedModuleMetadataValidator.INSTANCE, metadata);
    }

    @Test
    public void nullId() {
        blankId(null);
    }

    @Test
    public void emptyId() {
        blankId("");
    }

    @Test
    public void blankId() {
        blankId("   ");
    }

    private static void blankId(String id) {
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setId(id);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "id", "must be specified");
    }

    @Test
    public void idTooLong() {
        String id = RandomStringUtils.randomAlphanumeric(SharedModuleMetadataValidator.ID_MAX_LENGTH + 1);
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setId(id);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "id",
                "can't be more than " + SharedModuleMetadataValidator.ID_MAX_LENGTH + " characters");
    }

    @Test
    public void nullName() {
        blankName(null);
    }

    @Test
    public void emptyName() {
        blankName("");
    }

    @Test
    public void blankName() {
        blankName("   ");
    }

    private static void blankName(String name) {
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setName(name);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "name",
                "must be specified");
    }

    @Test
    public void nameTooLong() {
        String name = RandomStringUtils.randomAlphanumeric(SharedModuleMetadataValidator.NAME_MAX_LENGTH + 1);
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setName(name);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "name",
                "can't be more than " + SharedModuleMetadataValidator.NAME_MAX_LENGTH + " characters");
    }

    @Test
    public void notesTooLong() {
        String notes = RandomStringUtils.randomAlphanumeric(SharedModuleMetadataValidator.NOTES_MAX_LENGTH + 1);
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setNotes(notes);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "notes",
                "can't be more than " + SharedModuleMetadataValidator.NOTES_MAX_LENGTH + " characters");
    }

    @Test
    public void osTooLong() {
        String os = RandomStringUtils.randomAlphanumeric(SharedModuleMetadataValidator.OS_MAX_LENGTH + 1);
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setOs(os);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "os",
                "can't be more than " + SharedModuleMetadataValidator.OS_MAX_LENGTH + " characters");
    }

    @Test
    public void emptySchemaId() {
        blankSchemaId("");
    }

    @Test
    public void blankSchemaId() {
        blankSchemaId("   ");
    }

    private static void blankSchemaId(String schemaId) {
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setSchemaId(schemaId);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "schemaId",
                "can't be empty or blank");
    }

    @Test
    public void schemaIdTooLong() {
        String schemaId = RandomStringUtils.randomAlphanumeric(SharedModuleMetadataValidator.SCHEMA_ID_MAX_LENGTH + 1);
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setSchemaId(schemaId);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "schemaId",
                "can't be more than " + SharedModuleMetadataValidator.SCHEMA_ID_MAX_LENGTH + " characters");
    }

    @Test
    public void schemaIdWithoutRev() {
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setSchemaRevision(null);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "schemaRevision",
                "must be specified if schemaId is specified");
    }

    @Test
    public void negativeSchemaRev() {
        nonPositiveSchemaRev(-1);
    }

    @Test
    public void zeroSchemaRev() {
        nonPositiveSchemaRev(0);
    }

    private static void nonPositiveSchemaRev(int schemaRev) {
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setSchemaRevision(schemaRev);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "schemaRevision",
                "can't be zero or negative");
    }

    @Test
    public void schemaRevWithoutId() {
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setSchemaId(null);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "schemaId",
                "must be specified if schemaRevision is specified");
    }

    @Test
    public void negativeSurveyCreatedOn() {
        nonPositiveSurveyCreatedOn(-1);
    }

    @Test
    public void zeroSurveyCreatedOn() {
        nonPositiveSurveyCreatedOn(0);
    }

    private static void nonPositiveSurveyCreatedOn(long surveyCreatedOn) {
        SharedModuleMetadata metadata = makeValidMetadataWithSurvey();
        metadata.setSurveyCreatedOn(surveyCreatedOn);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "surveyCreatedOn",
                "can't be zero or negative");
    }

    @Test
    public void surveyCreatedOnWithoutGuid() {
        SharedModuleMetadata metadata = makeValidMetadataWithSurvey();
        metadata.setSurveyGuid(null);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "surveyGuid",
                "must be specified if surveyCreatedOn is specified");
    }

    @Test
    public void emptySurveyGuid() {
        blankSurveyGuid("");
    }

    @Test
    public void blankSurveyGuid() {
        blankSurveyGuid("   ");
    }

    private static void blankSurveyGuid(String surveyGuid) {
        SharedModuleMetadata metadata = makeValidMetadataWithSurvey();
        metadata.setSurveyGuid(surveyGuid);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "surveyGuid",
                "can't be empty or blank");
    }

    @Test
    public void surveyGuidTooLong() {
        String surveyGuid = RandomStringUtils.randomAlphanumeric(SharedModuleMetadataValidator.GUID_MAX_LENGTH + 1);
        SharedModuleMetadata metadata = makeValidMetadataWithSurvey();
        metadata.setSurveyGuid(surveyGuid);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "surveyGuid",
                "can't be more than " + SharedModuleMetadataValidator.GUID_MAX_LENGTH + " characters");
    }

    @Test
    public void surveyGuidWithoutCreatedOn() {
        SharedModuleMetadata metadata = makeValidMetadataWithSurvey();
        metadata.setSurveyCreatedOn(null);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "surveyCreatedOn",
                "must be specified if surveyGuid is specified");
    }

    @Test
    public void neitherSchemaNorSurvey() {
        SharedModuleMetadata metadata = makeValidMetadataWithoutSchemaOrSurvey();
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "sharedModuleMetadata",
                "must contain either schemaId or surveyGuid");
    }

    @Test
    public void bothSchemaAndSurvey() {
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setSurveyGuid(SURVEY_GUID);
        metadata.setSurveyCreatedOn(SURVEY_CREATED_ON);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "sharedModuleMetadata",
                "can't contain both schemaId and surveyGuid");
    }

    @Test
    public void negativeVersion() {
        nonPositiveVersion(-1);
    }

    @Test
    public void zeroVersion() {
        nonPositiveVersion(0);
    }

    private static void nonPositiveVersion(int version) {
        SharedModuleMetadata metadata = makeValidMetadataWithSchema();
        metadata.setVersion(version);
        TestUtils.assertValidatorMessage(SharedModuleMetadataValidator.INSTANCE, metadata, "version",
                "can't be zero or negative");
    }

    private static SharedModuleMetadata makeValidMetadataWithSchema() {
        SharedModuleMetadata metadata = makeValidMetadataWithoutSchemaOrSurvey();
        metadata.setSchemaId(SCHEMA_ID);
        metadata.setSchemaRevision(SCHEMA_REV);
        return metadata;
    }

    private static SharedModuleMetadata makeValidMetadataWithSurvey() {
        SharedModuleMetadata metadata = makeValidMetadataWithoutSchemaOrSurvey();
        metadata.setSurveyCreatedOn(SURVEY_CREATED_ON);
        metadata.setSurveyGuid(SURVEY_GUID);
        return metadata;
    }

    // slight misnomer: This isn't *quite* valid until you add a schema or survey.
    private static SharedModuleMetadata makeValidMetadataWithoutSchemaOrSurvey() {
        SharedModuleMetadata metadata = SharedModuleMetadata.create();
        metadata.setId(MODULE_ID);
        metadata.setName(MODULE_NAME);
        metadata.setVersion(MODULE_VERSION);
        return metadata;
    }
}
