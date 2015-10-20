package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConsentSignatureTest {
    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
    private static final long UNIX_TIMESTAMP = DateUtils.getCurrentMillisFromEpoch();
    
    private void assertMessage(InvalidEntityException e, String fieldName, String message) {
        assertEquals(message, e.getErrors().get(fieldName).get(0));
    }

    @Test
    public void nullName() {
        try {
            ConsentSignature.create(null, "1970-01-01", null, null, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "name", "name cannot be missing, null, or blank");
        }
    }

    @Test
    public void emptyName() {
        try {
            ConsentSignature.create("", "1970-01-01", null, null, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "name", "name cannot be missing, null, or blank");
        }
    }

    @Test
    public void nullBirthdate() {
        try {
            ConsentSignature.create("test name", null, null, null, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "birthdate", "birthdate cannot be missing, null, or blank");
        }
    }

    @Test
    public void emptyBirthdate() {
        try {
            ConsentSignature.create("test name", "", null, null, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "birthdate", "birthdate cannot be missing, null, or blank");
        }
    }

    @Test
    public void emptyImageData() {
        try {
            ConsentSignature.create("test name", "1970-01-01", "", "image/fake", UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "imageData", "imageData cannot be an empty string");
        }
    }

    @Test
    public void emptyImageMimeType() {
        try {
            ConsentSignature.create("test name", "1970-01-01", TestConstants.DUMMY_IMAGE_DATA, "", UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "imageMimeType", "imageMimeType cannot be an empty string");
        }
    }

    @Test
    public void imageDataWithoutMimeType() {
        try {
            ConsentSignature.create("test name", "1970-01-01", TestConstants.DUMMY_IMAGE_DATA, null, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("ConsentSignature If you specify one of imageData or imageMimeType, you must specify both"));
        }
    }

    @Test
    public void imageMimeTypeWithoutData() {
        try {
            ConsentSignature.create("test name", "1970-01-01", null, "image/fake", UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("ConsentSignature If you specify one of imageData or imageMimeType, you must specify both"));
        }
    }

    @Test
    public void happyCase() {
        ConsentSignature sig = ConsentSignature.create("test name", "1970-01-01", null, null, UNIX_TIMESTAMP);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertNull(sig.getImageData());
        assertNull(sig.getImageMimeType());
    }

    @Test
    public void withImage() {
        ConsentSignature sig = ConsentSignature.create("test name", "1970-01-01", TestConstants.DUMMY_IMAGE_DATA,
                "image/fake", UNIX_TIMESTAMP);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, sig.getImageData());
        assertEquals("image/fake", sig.getImageMimeType());
    }

    @Test
    public void jsonNoName() throws Exception {
        String jsonStr = "{\"birthdate\":\"1970-01-01\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        try {
            ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "name", "name cannot be missing, null, or blank");
        }
    }

    @Test
    public void jsonNullName() throws Exception {
        String jsonStr = "{\"name\":null, \"birthdate\":\"1970-01-01\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        try {
            ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "name", "name cannot be missing, null, or blank");
        }
    }

    @Test
    public void jsonEmptyName() throws Exception {
        String jsonStr = "{\"name\":\"\", \"birthdate\":\"1970-01-01\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        try {
            ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "name", "name cannot be missing, null, or blank");
        }
    }

    @Test
    public void jsonNoBirthdate() throws Exception {
        String jsonStr = "{\"name\":\"test name\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        try {
            ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "birthdate", "birthdate cannot be missing, null, or blank");
        }
    }

    @Test
    public void jsonNullBirthdate() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":null}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        try {
            ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "birthdate", "birthdate cannot be missing, null, or blank");
        }
    }

    @Test
    public void jsonEmptyBirthdate() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        try {
            ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
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
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        try {
            ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
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
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        try {
            ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
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
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        try {
            ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
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
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        try {
            ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains(
                    "ConsentSignature If you specify one of imageData or imageMimeType, you must specify both"));
        }
    }

    @Test
    public void jsonHappyCase() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"1970-01-01\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature sig = ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
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
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature sig = ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
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
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature sig = ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, sig.getImageData());
        assertEquals("image/fake", sig.getImageMimeType());
    }
    
    @Test
    public void signatureContainsSignedOnValue() throws Exception {
        String json = "{\"name\":\"test name\",\"birthdate\":\"1970-01-01\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(json);

        ConsentSignature sig = ConsentSignature.createFromJson(jsonNode, UNIX_TIMESTAMP);
        assertEquals(UNIX_TIMESTAMP, sig.getSignedOn());
    }
    
    @Test
    public void existingSignatureJsonDeserializesWithoutSignedOn() throws Exception {
        String json = "{\"name\":\"test name\",\"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(json, ConsentSignature.class);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertEquals(0, sig.getSignedOn());
    }
    
    @Test
    public void migrationConstructorUpdatesSignedOnValue() throws Exception {
        String json = "{\"name\":\"test name\",\"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(json, ConsentSignature.class);

        ConsentSignature updated = ConsentSignature.create(sig, UNIX_TIMESTAMP);
        assertEquals("test name", updated.getName());
        assertEquals("1970-01-01", updated.getBirthdate());
        assertEquals(UNIX_TIMESTAMP, updated.getSignedOn());
        
        try {
            ConsentSignature.create(sig, -1L);
        } catch(InvalidEntityException e) {
            assertMessage(e, "signedOn", "signedOn must be a valid signature timestamp");
        }
    }
    
    @Test
    public void equalsAndHashCodeAreCorrect() {
        EqualsVerifier.forClass(ConsentSignature.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
}
