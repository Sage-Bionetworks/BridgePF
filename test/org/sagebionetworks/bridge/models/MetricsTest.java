package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class MetricsTest {

    @Test
    public void test() throws Exception {
        // Create Metrics.
        String requestId = "12345";
        Metrics metrics = new Metrics(requestId);
        assertNotNull(metrics);

        // Validate cache key.
        assertEquals("12345:Metrics", metrics.getCacheKey());
        assertEquals("12345:Metrics", Metrics.getCacheKey(requestId));

        // Apply setters.
        metrics.setRecordId("test-record");

        // Validate JSON.
        final String json = metrics.toJsonString();
        assertNotNull(json);
        JsonNode metricsNode = BridgeObjectMapper.get().readTree(json);
        assertEquals("test-record", metricsNode.get("record_id").textValue());
        assertEquals(requestId, metricsNode.get("request_id").textValue());
        assertTrue(metricsNode.hasNonNull("start"));
        assertEquals(1, metricsNode.get("version").intValue());
    }

    @Test
    public void testSetStatus() {
        String requestId = "12345";
        Metrics metrics = new Metrics(requestId);
        metrics.setStatus(200);
        final String json = metrics.toJsonString();
        assertTrue(json.contains("\"status\":200"));
    }

    @Test
    public void testSetStudy() {
        String requestId = "12345";
        Metrics metrics = new Metrics(requestId);
        metrics.setStudy(null);
        String json = metrics.toJsonString();
        assertFalse(json.contains("\"study\":"));
        metrics.setStudy(" ");
        json = metrics.toJsonString();
        assertFalse(json.contains("\"study\":"));
        metrics.setStudy("api");
        json = metrics.toJsonString();
        assertTrue(json.contains("\"study\":\"api\""));
    }

    @Test
    public void testSetSession() {
        String requestId = "12345";
        Metrics metrics = new Metrics(requestId);
        metrics.setSessionId(null);
        String json = metrics.toJsonString();
        assertFalse(json.contains("\"session_id\":"));
        metrics.setSessionId("d839fe");
        json = metrics.toJsonString();
        assertTrue(json.contains("\"session_id\":\"d839fe\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorRequestIdMustNotBeNull() {
        new Metrics(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorRequestIdMustNotBeEmpty() {
        new Metrics(" ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetCacheKeyRequestIdMustNotBeNull() {
        Metrics.getCacheKey(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetCacheKeyRequestIdMustNotBeEmpty() {
        Metrics.getCacheKey(" ");
    }
}
