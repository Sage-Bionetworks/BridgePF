package org.sagebionetworks.bridge.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BridgeConfigTest {

    @Before
    public void before() {
        System.setProperty("bridge.pwd", "when.the.password.is.not.a.password");
        System.setProperty("bridge.salt", "when.the.salt.is.some.random.sea.salt");
        System.setProperty("bridge.user", "unit.test");
    }

    @After
    public void after() {
        System.clearProperty("bridge.pwd");
        System.clearProperty("bridge.salt");
        System.clearProperty("bridge.user");
        System.clearProperty("bridge.env");
    }

    @Test
    public void testNonExisting() {
        BridgeConfig config = new BridgeConfig();
        assertNull(config.getProperty("someFakePropertyThatDoesNotExist"));
    }

    @Test
    public void testEncryption() {
        System.setProperty("bridge.env", "local");
        BridgeConfig config = new BridgeConfig();
        assertEquals("example.value", config.getProperty("example.property"));
        assertEquals("example.value.encrypted", config.getProperty("example.property.encrypted"));
    }

    @Test
    public void testEnvironment() {
        System.setProperty("bridge.env", "dev");
        BridgeConfig config = new BridgeConfig();
        assertEquals("example.value.for.dev", config.getProperty("example.property"));
    }

    @Test
    public void testUser() {
        BridgeConfig config = new BridgeConfig();
        assertEquals("unit.test", config.getUser());
    }

    @Test
    public void testNumber() {
        BridgeConfig config = new BridgeConfig();
        assertEquals(2000, config.getPropertyAsInt("example.timeout"));
    }
}
