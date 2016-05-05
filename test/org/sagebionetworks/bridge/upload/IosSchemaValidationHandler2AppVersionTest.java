package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class IosSchemaValidationHandler2AppVersionTest {
    private static final String UPLOAD_ID = "test-upload";
    private static final String ERROR_MESSAGE = "Couldn't parse app version for upload " + UPLOAD_ID;

    @Test
    public void noAppVersion() throws Exception {
        // Set up context. We only ever write to this, so no init necessary.
        UploadValidationContext ctx = new UploadValidationContext();

        // Setup JSON. We don't care about any other attribute, so an empty JSON object is fine.
        String infoJsonText = "{}";
        JsonNode infoJsonNode = BridgeObjectMapper.get().readTree(infoJsonText);

        // Execute and validate.
        IosSchemaValidationHandler2.parseAppVersionFromInfoJson(ctx, UPLOAD_ID, infoJsonNode);
        assertNull(ctx.getAppVersion());
        assertEquals(1, ctx.getMessageList().size());
        assertEquals(ERROR_MESSAGE, ctx.getMessageList().get(0));
    }

    @Test
    public void nullAppVersion() throws Exception {
        // Set up context. We only ever write to this, so no init necessary.
        UploadValidationContext ctx = new UploadValidationContext();

        // Setup JSON. We don't care about any other attribute, so an empty JSON object is fine.
        String infoJsonText = "{\"appVersion\":null}";
        JsonNode infoJsonNode = BridgeObjectMapper.get().readTree(infoJsonText);

        // Execute and validate.
        IosSchemaValidationHandler2.parseAppVersionFromInfoJson(ctx, UPLOAD_ID, infoJsonNode);
        assertNull(ctx.getAppVersion());
        assertEquals(1, ctx.getMessageList().size());
        assertEquals(ERROR_MESSAGE, ctx.getMessageList().get(0));
    }

    @Test
    public void notAString() throws Exception {
        // Set up context. We only ever write to this, so no init necessary.
        UploadValidationContext ctx = new UploadValidationContext();

        // Setup JSON. We don't care about any other attribute, so an empty JSON object is fine.
        String infoJsonText = "{\"appVersion\":42}";
        JsonNode infoJsonNode = BridgeObjectMapper.get().readTree(infoJsonText);

        // Execute and validate.
        IosSchemaValidationHandler2.parseAppVersionFromInfoJson(ctx, UPLOAD_ID, infoJsonNode);
        assertNull(ctx.getAppVersion());
        assertEquals(1, ctx.getMessageList().size());
        assertEquals(ERROR_MESSAGE, ctx.getMessageList().get(0));
    }

    @Test
    public void wrongFormat() throws Exception {
        // Set up context. We only ever write to this, so no init necessary.
        UploadValidationContext ctx = new UploadValidationContext();

        // Setup JSON. We don't care about any other attribute, so an empty JSON object is fine.
        String infoJsonText = "{\"appVersion\":\"this is version 42\"}";
        JsonNode infoJsonNode = BridgeObjectMapper.get().readTree(infoJsonText);

        // Execute and validate.
        IosSchemaValidationHandler2.parseAppVersionFromInfoJson(ctx, UPLOAD_ID, infoJsonNode);
        assertNull(ctx.getAppVersion());
        assertEquals(1, ctx.getMessageList().size());
        assertEquals(ERROR_MESSAGE, ctx.getMessageList().get(0));
    }

    @Test
    public void happyCase() throws Exception {
        // Set up context. We only ever write to this, so no init necessary.
        UploadValidationContext ctx = new UploadValidationContext();

        // Setup JSON. We don't care about any other attribute, so an empty JSON object is fine.
        String infoJsonText = "{\"appVersion\":\"version 1.1.0, build 42\"}";
        JsonNode infoJsonNode = BridgeObjectMapper.get().readTree(infoJsonText);

        // Execute and validate.
        IosSchemaValidationHandler2.parseAppVersionFromInfoJson(ctx, UPLOAD_ID, infoJsonNode);
        assertEquals(42, ctx.getAppVersion().intValue());
        assertTrue(ctx.getMessageList().isEmpty());
    }
}
