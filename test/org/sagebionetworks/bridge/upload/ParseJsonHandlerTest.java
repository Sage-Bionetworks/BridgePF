package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

@SuppressWarnings("unchecked")
public class ParseJsonHandlerTest {
    @Test
    public void test() {
        // inputs
        Map<String, byte[]> inputMap = new HashMap<>();
        inputMap.put("foo", "{\"isJson\":true}".getBytes(Charsets.UTF_8));
        inputMap.put("bar", "This is not JSON".getBytes(Charsets.UTF_8));

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setUnzippedDataMap(inputMap);

        // execute and validate
        new ParseJsonHandler().handle(ctx);

        Map<String, byte[]> byteMap = ctx.getUnzippedDataMap();
        assertEquals(1, byteMap.size());
        assertEquals("This is not JSON", new String(byteMap.get("bar"), Charsets.UTF_8));

        Map<String, JsonNode> jsonDataMap = ctx.getJsonDataMap();
        assertEquals(1, jsonDataMap.size());
        Map<String, Object> jsonInnerMap = BridgeObjectMapper.get().convertValue(jsonDataMap.get("foo"), Map.class);
        assertEquals(1, jsonInnerMap.size());
        assertTrue((boolean) jsonInnerMap.get("isJson"));
    }
}
