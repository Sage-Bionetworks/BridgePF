package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.HashMap;

import com.fasterxml.jackson.databind.JsonNode;
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
    public void validateHappyCase() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().build();
        Validate.entityThrowingException(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission);
    }

    @Test
    public void nullAppVersion() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withAppVersion(null).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "appVersion",
                "is required");
    }

    @Test
    public void emptyAppVersion() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withAppVersion("").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "appVersion",
                "is required");
    }

    @Test
    public void blankAppVersion() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withAppVersion("   ").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "appVersion",
                "is required");
    }

    @Test
    public void nullCreatedOn() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withCreatedOn(null).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "createdOn",
                "is required");
    }

    @Test
    public void dataJavaNull() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withData(null).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "data", "is required");
    }

    @Test
    public void dataJsonNull() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withData(NullNode.getInstance()).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "data", "is required");
    }

    @Test
    public void dataArrayNode() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder()
                .withData(BridgeObjectMapper.get().createArrayNode()).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "data",
                "must be an object node");
    }

    @Test
    public void nullPhoneInfo() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withPhoneInfo(null).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "phoneInfo",
                "is required");
    }

    @Test
    public void emptyPhoneInfo() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withPhoneInfo("").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "phoneInfo",
                "is required");
    }

    @Test
    public void blankPhoneInfo() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withPhoneInfo("   ").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "phoneInfo",
                "is required");
    }

    @Test
    public void nullSchemaId() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withSchemaId(null).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "schemaId",
                "is required");
    }

    @Test
    public void emptySchemaId() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withSchemaId("").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "schemaId",
                "is required");
    }

    @Test
    public void blankSchemaId() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withSchemaId("   ").build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "schemaId",
                "is required");
    }

    @Test
    public void zeroSchemaRev() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withSchemaRevision(0).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "schemaRevision",
                "must be positive");
    }

    @Test
    public void negativeSchemaRev() {
        HealthDataSubmission healthDataSubmission = makeValidBuilder().withSchemaRevision(-1).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "schemaRevision",
                "must be positive");
    }

    // special case - unspecified schema rev will fail validation
    @Test
    public void unspecitiedSchemaRev() {
        HealthDataSubmission healthDataSubmission = new HealthDataSubmission.Builder().withAppVersion(APP_VERSION)
                .withCreatedOn(CREATED_ON).withData(DATA).withPhoneInfo(PHONE_INFO).withSchemaId(SCHEMA_ID).build();
        assertValidatorMessage(HealthDataSubmissionValidator.INSTANCE, healthDataSubmission, "schemaRevision",
                "must be positive");
    }

    private static HealthDataSubmission.Builder makeValidBuilder() {
        return new HealthDataSubmission.Builder().withAppVersion(APP_VERSION).withCreatedOn(CREATED_ON).withData(DATA)
                .withPhoneInfo(PHONE_INFO).withSchemaId(SCHEMA_ID).withSchemaRevision(SCHEMA_REV);
    }
}
