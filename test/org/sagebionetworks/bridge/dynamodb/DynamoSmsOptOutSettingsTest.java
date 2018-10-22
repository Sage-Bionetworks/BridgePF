package org.sagebionetworks.bridge.dynamodb;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.sms.SmsOptOutSettings;

public class DynamoSmsOptOutSettingsTest {
    private static final Map<String, Boolean> DUMMY_OPT_OUT_MAP_FOO = ImmutableMap.of("foo", true);
    private static final Map<String, Boolean> DUMMY_OPT_OUT_MAP_BAR = ImmutableMap.of("bar", true);
    private static final String PHONE_NUMBER = "+12065550123";

    @Test
    public void collectionsAreNonNull() {
        // Starts empty.
        DynamoSmsOptOutSettings optOutSettings = new DynamoSmsOptOutSettings();
        assertTrue(optOutSettings.getPromotionalOptOuts().isEmpty());
        assertTrue(optOutSettings.getTransactionalOptOuts().isEmpty());

        // Make sure setters work.
        optOutSettings.setPromotionalOptOuts(DUMMY_OPT_OUT_MAP_FOO);
        optOutSettings.setTransactionalOptOuts(DUMMY_OPT_OUT_MAP_BAR);
        assertEquals(DUMMY_OPT_OUT_MAP_FOO, optOutSettings.getPromotionalOptOuts());
        assertEquals(DUMMY_OPT_OUT_MAP_BAR, optOutSettings.getTransactionalOptOuts());

        // Setting to null makes them empty again.
        optOutSettings.setPromotionalOptOuts(null);
        optOutSettings.setTransactionalOptOuts(null);
        assertTrue(optOutSettings.getPromotionalOptOuts().isEmpty());
        assertTrue(optOutSettings.getTransactionalOptOuts().isEmpty());
    }

    @Test
    public void deserializeWithoutOptionalFields() throws Exception {
        // Start with JSON.
        String jsonText = "{\n" +
                "   \"phoneNumber\":\"" + PHONE_NUMBER + "\"\n" +
                "}";

        // Convert to POJO.
        SmsOptOutSettings optOutSettings = BridgeObjectMapper.get().readValue(jsonText, SmsOptOutSettings.class);
        assertEquals(PHONE_NUMBER, optOutSettings.getPhoneNumber());
        assertFalse(optOutSettings.getGlobalPromotionalOptOut());
        assertTrue(optOutSettings.getPromotionalOptOuts().isEmpty());
        assertTrue(optOutSettings.getTransactionalOptOuts().isEmpty());

        // Serializing is tested in another test.
    }

    @Test
    public void serialize() throws Exception {
        // Start with JSON.
        String jsonText = "{\n" +
                "   \"phoneNumber\":\"" + PHONE_NUMBER + "\",\n" +
                "   \"globalPromotionalOptOut\":true,\n" +
                "   \"promotionalOptOuts\":{\"foo\": true},\n" +
                "   \"transactionalOptOuts\":{\"bar\": true}\n" +
                "}";

        // Convert to POJO.
        SmsOptOutSettings optOutSettings = BridgeObjectMapper.get().readValue(jsonText, SmsOptOutSettings.class);
        assertEquals(PHONE_NUMBER, optOutSettings.getPhoneNumber());
        assertTrue(optOutSettings.getGlobalPromotionalOptOut());
        assertEquals(DUMMY_OPT_OUT_MAP_FOO, optOutSettings.getPromotionalOptOuts());
        assertEquals(DUMMY_OPT_OUT_MAP_BAR, optOutSettings.getTransactionalOptOuts());

        // Convert back to JSON.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(optOutSettings, JsonNode.class);
        assertEquals(PHONE_NUMBER, jsonNode.get("phoneNumber").textValue());
        assertTrue(jsonNode.get("globalPromotionalOptOut").booleanValue());

        JsonNode promotionalOptOutNode = jsonNode.get("promotionalOptOuts");
        assertEquals(1, promotionalOptOutNode.size());
        assertTrue(promotionalOptOutNode.get("foo").booleanValue());

        JsonNode transactionalOptOutNode = jsonNode.get("transactionalOptOuts");
        assertEquals(1, transactionalOptOutNode.size());
        assertTrue(transactionalOptOutNode.get("bar").booleanValue());
    }
}
