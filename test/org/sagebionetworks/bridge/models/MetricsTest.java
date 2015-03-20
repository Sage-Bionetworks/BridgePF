package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MetricsTest {

    @Test
    public void test() {
        String requestId = "12345";
        Metrics metrics = new Metrics(requestId);
        assertNotNull(metrics);
        assertEquals("12345:Metrics", metrics.getCacheKey());
        assertEquals("12345:Metrics", Metrics.getCacheKey(requestId));
        final String json = metrics.toJsonString();
        assertNotNull(json);
        assertTrue(json.contains("\"version\":1"));
        assertTrue(json.contains("\"start\":"));
        assertTrue(json.contains("\"request_id\":\"12345\""));
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
