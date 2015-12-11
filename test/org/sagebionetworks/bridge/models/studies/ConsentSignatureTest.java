package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.joda.time.DateTimeUtils;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;

import com.fasterxml.jackson.databind.JsonMappingException;

public class ConsentSignatureTest {
    private static final long UNIX_TIMESTAMP = DateUtils.getCurrentMillisFromEpoch();
    
    private void assertMessage(InvalidEntityException e, String fieldName, String message) {
        assertEquals(message, e.getErrors().get(fieldName).get(0));
    }
    
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(UNIX_TIMESTAMP);
    }
    
    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void nullName() {
        try {
            new ConsentSignature.Builder().withBirthdate("1970-01-01").withSignedOn(UNIX_TIMESTAMP).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "name", "name cannot be missing, null, or blank");
        }
    }

    @Test
    public void emptyName() {
        try {
            new ConsentSignature.Builder().withBirthdate("1970-01-01").withSignedOn(UNIX_TIMESTAMP).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "name", "name cannot be missing, null, or blank");
        }
    }

    @Test
    public void nullBirthdate() {
        try {
            new ConsentSignature.Builder().withName("test name").withSignedOn(UNIX_TIMESTAMP).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "birthdate", "birthdate cannot be missing, null, or blank");
        }
    }

    @Test
    public void emptyBirthdate() {
        try {
            new ConsentSignature.Builder().withName("test name").withBirthdate("").withSignedOn(UNIX_TIMESTAMP).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "birthdate", "birthdate cannot be missing, null, or blank");
        }
    }

    @Test
    public void emptyImageData() {
        try {
            new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageData("").withImageMimeType("image/fake").withSignedOn(UNIX_TIMESTAMP).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "imageData", "imageData cannot be an empty string");
        }
    }

    @Test
    public void emptyImageMimeType() {
        try {
            new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageData(TestConstants.DUMMY_IMAGE_DATA).withImageMimeType("").withSignedOn(UNIX_TIMESTAMP).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "imageMimeType", "imageMimeType cannot be an empty string");
        }
    }

    @Test
    public void imageDataWithoutMimeType() {
        try {
            new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageData(TestConstants.DUMMY_IMAGE_DATA).withSignedOn(UNIX_TIMESTAMP).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("ConsentSignature If you specify one of imageData or imageMimeType, you must specify both"));
        }
    }

    @Test
    public void imageMimeTypeWithoutData() {
        try {
            new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageMimeType("image/fake").withSignedOn(UNIX_TIMESTAMP).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("ConsentSignature If you specify one of imageData or imageMimeType, you must specify both"));
        }
    }

    @Test
    public void happyCase() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
            .withSignedOn(UNIX_TIMESTAMP).build();
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertNull(sig.getImageData());
        assertNull(sig.getImageMimeType());
    }

    @Test
    public void withImage() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageData(TestConstants.DUMMY_IMAGE_DATA).withImageMimeType("image/fake")
                .withSignedOn(UNIX_TIMESTAMP).build();
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, sig.getImageData());
        assertEquals("image/fake", sig.getImageMimeType());
    }

    @Test
    public void jsonNoName() throws Exception {
        String jsonStr = "{\"birthdate\":\"1970-01-01\"}";
        try {
            BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);

            fail("Should have thrown an exception");
        } catch(JsonMappingException jme) {
            InvalidEntityException e = (InvalidEntityException)jme.getCause();
            assertMessage(e, "name", "name cannot be missing, null, or blank");
        }
    }

    @Test
    public void jsonNullName() throws Exception {
        String jsonStr = "{\"name\":null, \"birthdate\":\"1970-01-01\"}";
        try {
            BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
            fail("Should have thrown an exception");
        } catch(JsonMappingException jme) {
            InvalidEntityException e = (InvalidEntityException)jme.getCause();
            assertMessage(e, "name", "name cannot be missing, null, or blank");
        }
    }

    @Test
    public void jsonEmptyName() throws Exception {
        String jsonStr = "{\"name\":\"\", \"birthdate\":\"1970-01-01\"}";
        try {
            BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
            fail("Should have thrown an exception");
        } catch(JsonMappingException jme) {
            InvalidEntityException e = (InvalidEntityException)jme.getCause();
            assertMessage(e, "name", "name cannot be missing, null, or blank");
        }
    }

    @Test
    public void jsonNoBirthdate() throws Exception {
        String jsonStr = "{\"name\":\"test name\"}";
        try {
            BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
            fail("Should have thrown an exception");
        } catch(JsonMappingException jme) {
            InvalidEntityException e = (InvalidEntityException)jme.getCause();
            assertMessage(e, "birthdate", "birthdate cannot be missing, null, or blank");
        }
    }

    @Test
    public void jsonNullBirthdate() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":null}";
        try {
            BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
            fail("Should have thrown an exception");
        } catch(JsonMappingException jme) {
            InvalidEntityException e = (InvalidEntityException)jme.getCause();
            assertMessage(e, "birthdate", "birthdate cannot be missing, null, or blank");
        }
    }

    @Test
    public void jsonEmptyBirthdate() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"\"}";
        try {
            BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
            fail("Should have thrown an exception");
        } catch(JsonMappingException jme) {
            InvalidEntityException e = (InvalidEntityException)jme.getCause();
            assertMessage(e, "birthdate", "birthdate cannot be missing, null, or blank");
        }
    }

    @Test
    public void jsonEmptyImageData() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"\",\n" +
                "   \"imageMimeType\":\"image/fake\"\n" +
                "}";
        try {
            BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
            fail("Should have thrown an exception");
        } catch(JsonMappingException jme) {
            InvalidEntityException e = (InvalidEntityException)jme.getCause();
            assertMessage(e, "imageData", "imageData cannot be an empty string");
        }
    }

    @Test
    public void jsonEmptyImageMimeType() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\",\n" +
                "   \"imageMimeType\":\"\"\n" +
                "}";
        try {
            BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
            fail("Should have thrown an exception");
        } catch(JsonMappingException jme) {
            InvalidEntityException e = (InvalidEntityException)jme.getCause();
            assertMessage(e, "imageMimeType", "imageMimeType cannot be an empty string");
        }
    }

    @Test
    public void jsonImageDataWithoutMimeType() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\"\n" +
                "}";
        try {
            BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
            fail("Should have thrown an exception");
        } catch(JsonMappingException jme) {
            InvalidEntityException e = (InvalidEntityException)jme.getCause();
            assertTrue(e.getMessage().contains(
                "ConsentSignature If you specify one of imageData or imageMimeType, you must specify both"));
        }
    }

    @Test
    public void jsonImageMimeTypeWithoutData() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageMimeType\":\"image/fake\"\n" +
                "}";
        try {
            BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
            fail("Should have thrown an exception");
        } catch(JsonMappingException jme) {
            InvalidEntityException e = (InvalidEntityException)jme.getCause();
            assertTrue(e.getMessage().contains(
                    "ConsentSignature If you specify one of imageData or imageMimeType, you must specify both"));
        }
    }

    @Test
    public void jsonHappyCase() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertNull(sig.getImageData());
        assertNull(sig.getImageMimeType());
    }

    @Test
    public void jsonHappyCaseNullImage() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":null,\n" +
                "   \"imageMimeType\":null\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertNull(sig.getImageData());
        assertNull(sig.getImageMimeType());
    }

    @Test
    public void jsonHappyCaseWithImage() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\",\n" +
                "   \"imageMimeType\":\"image/fake\"\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, sig.getImageData());
        assertEquals("image/fake", sig.getImageMimeType());
        assertEquals(UNIX_TIMESTAMP, sig.getSignedOn());
    }
    
    @Test
    public void existingSignatureJsonDeserializesWithoutSignedOn() throws Exception {
        String json = "{\"name\":\"test name\",\"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(json, ConsentSignature.class);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertEquals(UNIX_TIMESTAMP, sig.getSignedOn());
    }
    
    @Test
    public void migrationConstructorUpdatesSignedOnValue() throws Exception {
        String json = "{\"name\":\"test name\",\"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(json, ConsentSignature.class);

        ConsentSignature updated = new ConsentSignature.Builder().withConsentSignature(sig).withSignedOn(UNIX_TIMESTAMP).build();
        assertEquals("test name", updated.getName());
        assertEquals("1970-01-01", updated.getBirthdate());
        assertEquals(UNIX_TIMESTAMP, updated.getSignedOn());
        
        json = "{\"name\":\"test name\",\"birthdate\":\"1970-01-01\",\"signedOn\":-10}";
        sig = BridgeObjectMapper.get().readValue(json, ConsentSignature.class);
        assertEquals(UNIX_TIMESTAMP, sig.getSignedOn());
    }
    
    @Test
    public void equalsAndHashCodeAreCorrect() {
        EqualsVerifier.forClass(ConsentSignature.class).allFieldsShouldBeUsed().verify();
    }
}
