package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;

public class ConsentSignatureImageTest {
    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

    @Test(expected = NullPointerException.class)
    public void nullData() {
        new ConsentSignatureImage(null, "image/gif");
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyData() {
        new ConsentSignatureImage("", "image/gif");
    }

    @Test(expected = NullPointerException.class)
    public void nullMimeType() {
        new ConsentSignatureImage(TestConstants.DUMMY_IMAGE_DATA, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyMimeType() {
        new ConsentSignatureImage(TestConstants.DUMMY_IMAGE_DATA, "");
    }

    @Test
    public void happyCase() {
        ConsentSignatureImage sigImg = new ConsentSignatureImage(TestConstants.DUMMY_IMAGE_DATA, "image/gif");
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, sigImg.getData());
        assertEquals("image/gif", sigImg.getMimeType());
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNoData() throws Exception {
        String jsonStr = "{\"mimeType\":\"image/gif\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignatureImage.fromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNullData() throws Exception {
        String jsonStr = "{\"data\":null, \"mimeType\":\"image/gif\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignatureImage.fromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonEmptyData() throws Exception {
        String jsonStr = "{\"data\":\"\", \"mimeType\":\"image/gif\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignatureImage.fromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNoMimeType() throws Exception {
        String jsonStr = "{\"data\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignatureImage.fromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonNullMimeType() throws Exception {
        String jsonStr = "{\"data\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\", \"mimeType\":null}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignatureImage.fromJson(jsonNode);
    }

    @Test(expected = InvalidEntityException.class)
    public void jsonEmptyMimeType() throws Exception {
        String jsonStr = "{\"data\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\", \"mimeType\":\"\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignatureImage.fromJson(jsonNode);
    }

    @Test
    public void jsonHappyCase() throws Exception {
        String jsonStr = "{\"data\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\", \"mimeType\":\"image/gif\"}";
        JsonNode jsonNode = JSON_OBJECT_MAPPER.readTree(jsonStr);
        ConsentSignatureImage sigImg = ConsentSignatureImage.fromJson(jsonNode);
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, sigImg.getData());
        assertEquals("image/gif", sigImg.getMimeType());
    }
}
