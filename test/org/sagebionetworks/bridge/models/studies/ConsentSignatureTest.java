package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;

public class ConsentSignatureTest {
    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

    @Test(expected = InvalidEntityException.class)
    public void nullName() {
        ConsentSignature.create(null, "1970-01-01", null, null);
    }

    @Test(expected = InvalidEntityException.class)
    public void emptyName() {
        ConsentSignature.create("", "1970-01-01", null, null);
    }

    @Test(expected = InvalidEntityException.class)
    public void nullBirthdate() {
        ConsentSignature.create("test name", null, null, null);
    }

    @Test(expected = InvalidEntityException.class)
    public void emptyBirthdate() {
        ConsentSignature.create("test name", "", null, null);
    }

    @Test(expected = InvalidEntityException.class)
    public void emptyImageData() {
        ConsentSignature.create("test name", "1970-01-01", "", "image/fake");
    }

    @Test(expected = InvalidEntityException.class)
    public void emptyImageMimeType() {
        ConsentSignature.create("test name", "1970-01-01", TestConstants.DUMMY_IMAGE_DATA, "");
    }

    @Test(expected = InvalidEntityException.class)
    public void imageDataWithoutMimeType() {
        ConsentSignature.create("test name", "1970-01-01", TestConstants.DUMMY_IMAGE_DATA, null);
    }

    @Test(expected = InvalidEntityException.class)
    public void imageMimeTypeWithoutData() {
        ConsentSignature.create("test name", "1970-01-01", null, "image/fake");
    }

    @Test
    public void happyCase() {
        ConsentSignature sig = ConsentSignature.create("test name", "1970-01-01", null, null);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertNull(sig.getImageData());
        assertNull(sig.getImageMimeType());
    }

    @Test
    public void withImage() {
        ConsentSignature sig = ConsentSignature.create("test name", "1970-01-01", TestConstants.DUMMY_IMAGE_DATA,
                "image/fake");
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, sig.getImageData());
        assertEquals("image/fake", sig.getImageMimeType());
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNoName() throws Exception {
        String jsonStr = "{\"birthdate\":\"1970-01-01\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.createFromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNullName() throws Exception {
        String jsonStr = "{\"name\":null, \"birthdate\":\"1970-01-01\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.createFromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonEmptyName() throws Exception {
        String jsonStr = "{\"name\":\"\", \"birthdate\":\"1970-01-01\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.createFromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNoBirthdate() throws Exception {
        String jsonStr = "{\"name\":\"test name\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.createFromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNullBirthdate() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":null}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.createFromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonEmptyBirthdate() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.createFromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonEmptyImageData() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"\",\n" +
                "   \"imageMimeType\":\"image/fake\"\n" +
                "}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.createFromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonEmptyImageMimeType() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\",\n" +
                "   \"imageMimeType\":\"\"\n" +
                "}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.createFromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonImageDataWithoutMimeType() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\"\n" +
                "}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.createFromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonImageMimeTypeWithoutData() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageMimeType\":\"image/fake\"\n" +
                "}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.createFromJson(jsonNode);
    }

    @Test
    public void jsonHappyCase() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"1970-01-01\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature sig = ConsentSignature.createFromJson(jsonNode);
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
        ConsentSignature sig = ConsentSignature.createFromJson(jsonNode);
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
        ConsentSignature sig = ConsentSignature.createFromJson(jsonNode);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, sig.getImageData());
        assertEquals("image/fake", sig.getImageMimeType());
    }
}
