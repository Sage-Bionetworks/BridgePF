package controllers;

import static org.junit.Assert.assertEquals;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.httpclient.HttpStatus;
import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.UploadRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class UploadControllerTest {
    
    // The other tests in this class don't go through the controller; we want to do that
    // because we want to verify that we get the right type back. This could easily
    // be broken since there's no external test and we could switch the object internally.
    @Test
    public void uploadRequestHasCorrectType() {
        BridgeObjectMapper mapper = new BridgeObjectMapper();
        
        JsonNode node = mapper.valueToTree(new UploadRequest());
        assertEquals("Type is UploadRequest", "UploadRequest", node.get("type").asText());
    }

    @Test
    public void testValidateRequest() {

        // A valid case
        final String message = "testValidateRequest";
        {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("name", this.getClass().getSimpleName());
            node.put("contentType", "text/plain");
            node.put("contentLength", message.getBytes().length);
            node.put("contentMd5", Base64.encodeBase64String(DigestUtils.md5(message)));
            UploadRequest uploadRequest = UploadRequest.fromJson(node);
            UploadController controller = new UploadController();
            controller.validateRequest(uploadRequest);
        }

        try {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("contentType", "text/plain");
            node.put("contentLength", message.getBytes().length);
            node.put("contentMd5", Base64.encodeBase64String(DigestUtils.md5(message)));
            UploadRequest uploadRequest = UploadRequest.fromJson(node);
            UploadController controller = new UploadController();
            controller.validateRequest(uploadRequest);
        } catch (BridgeServiceException e) {
            assertEquals("Name missing", HttpStatus.SC_BAD_REQUEST, e.getStatusCode());
        }

        try {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("name", this.getClass().getSimpleName());
            node.put("contentLength", message.getBytes().length);
            node.put("contentMd5", Base64.encodeBase64String(DigestUtils.md5(message)));
            UploadRequest uploadRequest = UploadRequest.fromJson(node);
            UploadController controller = new UploadController();
            controller.validateRequest(uploadRequest);
        } catch (BridgeServiceException e) {
            assertEquals("Content type missing", HttpStatus.SC_BAD_REQUEST, e.getStatusCode());
        }

        try {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("name", this.getClass().getSimpleName());
            node.put("contentType", "text/plain");
            node.put("contentMd5", Base64.encodeBase64String(DigestUtils.md5(message)));
            UploadRequest uploadRequest = UploadRequest.fromJson(node);
            UploadController controller = new UploadController();
            controller.validateRequest(uploadRequest);
        } catch (BridgeServiceException e) {
            assertEquals("Content length missing", HttpStatus.SC_BAD_REQUEST, e.getStatusCode());
        }

        try {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("name", this.getClass().getSimpleName());
            node.put("contentType", "text/plain");
            node.put("contentLength", 11000000L);
            node.put("contentMd5", Base64.encodeBase64String(DigestUtils.md5(message)));
            UploadRequest uploadRequest = UploadRequest.fromJson(node);
            UploadController controller = new UploadController();
            controller.validateRequest(uploadRequest);
        } catch (BridgeServiceException e) {
            assertEquals("Content length > 10 MB", HttpStatus.SC_BAD_REQUEST, e.getStatusCode());
        }

        try {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("name", this.getClass().getSimpleName());
            node.put("contentType", "text/plain");
            node.put("contentLength", message.getBytes().length);
            node.put("contentMd5", DigestUtils.md5(message));
            UploadRequest uploadRequest = UploadRequest.fromJson(node);
            UploadController controller = new UploadController();
            controller.validateRequest(uploadRequest);
        } catch (BridgeServiceException e) {
            assertEquals("MD5 not base64 encoded", HttpStatus.SC_BAD_REQUEST, e.getStatusCode());
        }
    }
}
