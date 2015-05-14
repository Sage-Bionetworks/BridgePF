package org.sagebionetworks.bridge.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BridgeConfigTest {

    private static final File CONF_FILE = new File("test/conf/bridge.conf");

    @Before
    public void before() {
        System.setProperty("bridge.user", "unit.test");
        System.setProperty("bridge.env", "local");
    }

    @After
    public void after() {
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
    public void testEnvironment() {
        System.setProperty("bridge.env", "dev");
        BridgeConfig config = new BridgeConfig(CONF_FILE);
        assertEquals("example.value.for.dev",
                config.getProperty("example.property"));
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

    @Test
    public void testList() {
        BridgeConfig config = new BridgeConfig(CONF_FILE);
        List<String> list = config.getPropertyAsList("example.property");
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("example.value", list.get(0));
        list = config.getPropertyAsList("example.list");
        assertNotNull(list);
        assertEquals(3, list.size());
        assertEquals("a", list.get(0));
        assertEquals("bc", list.get(1));
        assertEquals("d", list.get(2));
    }
}
