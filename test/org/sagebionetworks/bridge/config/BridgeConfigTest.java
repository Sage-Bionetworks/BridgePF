package org.sagebionetworks.bridge.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BridgeConfigTest {

    @Test
    public void testDefault() {
        BridgeConfig config = new BridgeConfig();
        assertTrue(config.isLocal());
        assertEquals("fake.aws.key", config.getProperty("aws.key"));
        assertEquals("fake.aws.secret.key", config.getProperty("aws.secret.key"));
        assertNull(config.getProperty("someFakePropertyThatDoesNotExist"));
    }

    @Test
    public void testMockedProd() {
        System.setProperty("bridge.env", "prod");
        System.setProperty("aws.key", "myFakeTestKey");
        BridgeConfig config = new BridgeConfig();
        assertFalse(config.isLocal());
        assertFalse(config.isDevelopment());
        assertTrue(config.isProduction());
        assertEquals("myFakeTestKey", config.getProperty("aws.key"));
        System.clearProperty("bridge.env");
        System.clearProperty("aws.key");
    }
}
