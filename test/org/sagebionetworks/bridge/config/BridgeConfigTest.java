package org.sagebionetworks.bridge.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BridgeConfigTest {

    private static final File CONF_FILE = new File("test/conf/bridge.conf");

    @Before
    public void before() {
        System.setProperty("bridge.pwd", "when.the.password.is.not.a.password");
        System.setProperty("bridge.salt", "when.the.salt.is.some.random.sea.salt");
        System.setProperty("bridge.user", "unit.test");
        System.setProperty("bridge.env", "local");
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
        BridgeConfig config = new BridgeConfig(CONF_FILE);
        assertNull(config.getProperty("someFakePropertyThatDoesNotExist"));
    }

    @Test
    public void testEncryption() {
        BridgeConfig config = new BridgeConfig(CONF_FILE);
        assertEquals("example.value", config.getProperty("example.property"));
        assertEquals("example.value.encrypted", config.getProperty("example.property.encrypted"));
    }

    @Test
    public void testEnvironment() {
        System.setProperty("bridge.env", "dev");
        BridgeConfig config = new BridgeConfig(CONF_FILE);
        assertEquals("example.value.for.dev", config.getProperty("example.property"));
    }

    @Test
    public void testUser() {
        BridgeConfig config = new BridgeConfig(CONF_FILE);
        assertEquals("unit.test", config.getUser());
    }

    @Test
    public void testNumber() {
        BridgeConfig config = new BridgeConfig(CONF_FILE);
        assertEquals(2000, config.getPropertyAsInt("example.timeout"));
    }
}
