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
    private static final ConsentSignatureImage DUMMY_SIGNATURE_IMAGE = new ConsentSignatureImage(
            TestConstants.DUMMY_IMAGE_DATA, "image/gif");
    private static final String DUMMY_SIGNATURE_IMAGE_JSON = "{\"data\":\"" + TestConstants.DUMMY_IMAGE_DATA
            + "\", \"mimeType\":\"image/gif\"}";
    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

    @Test(expected = NullPointerException.class)
    public void nullName() {
        new ConsentSignature(null, "1970-01-01", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyName() {
        new ConsentSignature("", "1970-01-01", null);
    }

    @Test(expected = NullPointerException.class)
    public void nullBirthdate() {
        new ConsentSignature("test name", null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyBirthdate() {
        new ConsentSignature("test name", "", null);
    }

    @Test
    public void happyCase() {
        ConsentSignature sig = new ConsentSignature("test name", "1970-01-01", null);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertNull(sig.getImage());
    }

    @Test
    public void withImage() {
        ConsentSignature sig = new ConsentSignature("test name", "1970-01-01", DUMMY_SIGNATURE_IMAGE);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertSame(DUMMY_SIGNATURE_IMAGE, sig.getImage());
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNoName() throws Exception {
        String jsonStr = "{\"birthdate\":\"1970-01-01\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.fromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNullName() throws Exception {
        String jsonStr = "{\"name\":null, \"birthdate\":\"1970-01-01\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.fromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonEmptyName() throws Exception {
        String jsonStr = "{\"name\":\"\", \"birthdate\":\"1970-01-01\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.fromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNoBirthdate() throws Exception {
        String jsonStr = "{\"name\":\"test name\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.fromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNullBirthdate() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":null}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.fromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonEmptyBirthdate() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.fromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonInvalidImage() throws Exception {
        String jsonStr =
                "{\"name\":\"test name\", \"birthdate\":\"1970-01-01\", \"image\":{\"fake key\":\"fake value\"}}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature.fromJson(jsonNode);
    }

    @Test
    public void jsonHappyCase() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"1970-01-01\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature sig = ConsentSignature.fromJson(jsonNode);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertNull(sig.getImage());
    }

    @Test
    public void jsonNullImage() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"1970-01-01\", \"image\":null}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature sig = ConsentSignature.fromJson(jsonNode);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertNull(sig.getImage());
    }

    @Test
    public void jsonWithImage() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"1970-01-01\", \"image\":"
                + DUMMY_SIGNATURE_IMAGE_JSON + "}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignature sig = ConsentSignature.fromJson(jsonNode);
        assertEquals("test name", sig.getName());
        assertEquals("1970-01-01", sig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, sig.getImage().getData());
        assertEquals("image/gif", sig.getImage().getMimeType());
    }
}
