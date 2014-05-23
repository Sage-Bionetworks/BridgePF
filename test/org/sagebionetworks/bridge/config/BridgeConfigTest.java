package org.sagebionetworks.bridge.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
    public void testDefault() {
        BridgeConfig config = new BridgeConfig();
        Properties props = (Properties)ReflectionTestUtils.getField(config, "properties");
        props.remove("bridge.env");
        assertTrue(config.isStub());
        assertNull(config.getProperty("someFakePropertyThatDoesNotExist"));
    }

    @Test
    public void testEncryption() {
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
}
