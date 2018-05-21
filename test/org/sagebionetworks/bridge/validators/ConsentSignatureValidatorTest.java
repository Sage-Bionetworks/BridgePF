package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.time.DateUtils;

public class ConsentSignatureValidatorTest {
    private static final DateTime NOW = DateTime.parse("2022-02-21T00:00:00.000Z");
    private static final long SIGNED_ON_TIMESTAMP = DateUtils.getCurrentMillisFromEpoch();
    private ConsentSignatureValidator validator;
    
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(NOW.getMillis());
        validator = new ConsentSignatureValidator(0);
    }

    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void nullName() {
        ConsentSignature sig = new ConsentSignature.Builder().withBirthdate("1970-01-01")
                .withSignedOn(SIGNED_ON_TIMESTAMP).build();
        assertValidatorMessage(validator, sig, "name", "cannot be missing, null, or blank");
    }

    @Test
    public void emptyName() {
        ConsentSignature sig = new ConsentSignature.Builder().withBirthdate("1970-01-01")
                .withSignedOn(SIGNED_ON_TIMESTAMP).build();
        assertValidatorMessage(validator, sig, "name", "cannot be missing, null, or blank");
    }

    @Test
    public void nullBirthdate() {
        validator = new ConsentSignatureValidator(18);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withSignedOn(SIGNED_ON_TIMESTAMP).build();
        assertValidatorMessage(validator, sig, "birthdate", "cannot be missing, null, or blank");
    }
    
    @Test
    public void nullBirthdateOKWithoutAgeLimit() {
        validator = new ConsentSignatureValidator(0);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withSignedOn(SIGNED_ON_TIMESTAMP).build();
        Validate.entityThrowingException(validator, sig);
    }

    @Test
    public void emptyBirthdate() {
        validator = new ConsentSignatureValidator(18);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("")
                .withSignedOn(SIGNED_ON_TIMESTAMP).build();
        assertValidatorMessage(validator, sig, "birthdate", "cannot be missing, null, or blank");
    }

    @Test
    public void emptyImageData() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageData("").withImageMimeType("image/fake").withSignedOn(SIGNED_ON_TIMESTAMP).build();
        assertValidatorMessage(validator, sig, "imageData", "cannot be an empty string");
    }

    @Test
    public void emptyImageMimeType() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageData(TestConstants.DUMMY_IMAGE_DATA).withImageMimeType("").withSignedOn(SIGNED_ON_TIMESTAMP)
                .build();
        assertValidatorMessage(validator, sig, "imageMimeType", "cannot be an empty string");
    }

    @Test
    public void imageDataWithoutMimeType() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageData(TestConstants.DUMMY_IMAGE_DATA).withSignedOn(SIGNED_ON_TIMESTAMP).build();
        try {
            Validate.entityThrowingException(validator, sig);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains(
                    "ConsentSignature must specify imageData and imageMimeType if you specify either of them"));
        }        
    }

    @Test
    public void imageMimeTypeWithoutData() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageMimeType("image/fake").withSignedOn(SIGNED_ON_TIMESTAMP).build();
        try {
            Validate.entityThrowingException(validator, sig);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains(
                    "ConsentSignature must specify imageData and imageMimeType if you specify either of them"));
        }
    }

    @Test
    public void jsonNoName() throws Exception {
        ConsentSignature sig = new ConsentSignature.Builder().withBirthdate("1970-01-01").build();
        assertValidatorMessage(validator, sig, "name", "cannot be missing, null, or blank");
    }

    @Test
    public void jsonNullName() throws Exception {
        String jsonStr = "{\"name\":null, \"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "name", "cannot be missing, null, or blank");
    }

    @Test
    public void jsonEmptyName() throws Exception {
        String jsonStr = "{\"name\":\"\", \"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "name", "cannot be missing, null, or blank");
    }

    @Test
    public void jsonNoBirthdate() throws Exception {
        validator = new ConsentSignatureValidator(18);
        String jsonStr = "{\"name\":\"test name\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "birthdate", "cannot be missing, null, or blank");
    }

    @Test
    public void jsonNullBirthdate() throws Exception {
        validator = new ConsentSignatureValidator(18);
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":null}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "birthdate", "cannot be missing, null, or blank");
    }

    @Test
    public void jsonEmptyBirthdate() throws Exception {
        validator = new ConsentSignatureValidator(18);
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "birthdate", "cannot be missing, null, or blank");
    }

    @Test
    public void jsonEmptyImageData() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"\",\n" +
                "   \"imageMimeType\":\"image/fake\"\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "imageData", "cannot be an empty string");
    }

    @Test
    public void jsonEmptyImageMimeType() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\",\n" +
                "   \"imageMimeType\":\"\"\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "imageMimeType", "cannot be an empty string");
    }

    @Test
    public void jsonImageDataWithoutMimeType() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\"\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        try {
            Validate.entityThrowingException(validator, sig);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains(
                    "ConsentSignature must specify imageData and imageMimeType if you specify either of them"));
        }
    }

    @Test
    public void jsonImageMimeTypeWithoutData() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageMimeType\":\"image/fake\"\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        try {
            Validate.entityThrowingException(validator, sig);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains(
                    "ConsentSignature must specify imageData and imageMimeType if you specify either of them"));
        }
    }

    @Test
    public void minAgeLimitButNoBirthdate() {
        validator = new ConsentSignatureValidator(18);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").build();
        assertValidatorMessage(validator, sig, "birthdate", "cannot be missing, null, or blank");
    }
    
    @Test
    public void minAgeLimitButBirthdateTooRecent() {
        String birthdate = NOW.minusYears(18).plusDays(1).toLocalDate().toString();
        validator = new ConsentSignatureValidator(18);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate(birthdate).build();
        assertValidatorMessage(validator, sig, "birthdate", "too recent (the study requires participants to be 18 years of age or older).");
    }
    
    @Test
    public void minAgeLimitBirthdateOK() {
        String birthdate = NOW.minusYears(18).toLocalDate().toString();
        validator = new ConsentSignatureValidator(18);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate(birthdate).build();
        Validate.entityThrowingException(validator, sig);
    }
    
    @Test
    public void minAgeLimitBirthdateGarbled() {
        validator = new ConsentSignatureValidator(18);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("15 May 2018").build();
        assertValidatorMessage(validator, sig, "birthdate", "is invalid (required format: YYYY-MM-DD)");
    }
    
    @Test
    public void optionalBirthdateGarbled() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("15 May 2018").build();
        assertValidatorMessage(validator, sig, "birthdate", "is invalid (required format: YYYY-MM-DD)");
    }
}
