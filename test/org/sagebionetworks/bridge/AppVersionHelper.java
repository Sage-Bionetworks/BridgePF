package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Helper method that uses reflection to allow us to test for both min/maxAppVersion for any class with these
// methods
public class AppVersionHelper {
    @SuppressWarnings("unchecked")
    public static void testAppVersionHelper(Class<?> objClass) throws Exception {
        // test min and max separately
        testAppVersionHelper(objClass, "Min");
        testAppVersionHelper(objClass, "Max");

        // test getAppVersionOperatingSystems
        Object obj = objClass.newInstance();
        Method setMinMethod = objClass.getMethod("setMinAppVersion", String.class, Integer.class);
        Method setMaxMethod = objClass.getMethod("setMinAppVersion", String.class, Integer.class);

        // set a bunch of mins and maxes with some overlap, some non-overlap
        setMinMethod.invoke(obj, "foo", 1);
        setMinMethod.invoke(obj, "bar", 2);
        setMinMethod.invoke(obj, "baz", 3);
        setMinMethod.invoke(obj, "overlap", 4);
        setMaxMethod.invoke(obj, "qwerty", 5);
        setMaxMethod.invoke(obj, "asdf", 6);
        setMaxMethod.invoke(obj, "jkl;", 7);
        setMaxMethod.invoke(obj, "overlap", 8);

        // validate result
        Method getOsKeysMethod = objClass.getMethod("getAppVersionOperatingSystems");
        Set<String> osKeySet = (Set<String>) getOsKeysMethod.invoke(obj);
        assertEquals(7, osKeySet.size());
        assertTrue(osKeySet.contains("foo"));
        assertTrue(osKeySet.contains("bar"));
        assertTrue(osKeySet.contains("baz"));
        assertTrue(osKeySet.contains("qwerty"));
        assertTrue(osKeySet.contains("asdf"));
        assertTrue(osKeySet.contains("jkl;"));
        assertTrue(osKeySet.contains("overlap"));
    }

    @SuppressWarnings("unchecked")
    private static void testAppVersionHelper(Class<?> objClass, String prefix) throws Exception {
        Object obj = objClass.newInstance();
        Method getMapMethod = objClass.getMethod("get" + prefix + "AppVersions");
        Method setMapMethod = objClass.getMethod("set" + prefix + "AppVersions", Map.class);
        Method getByOsNameMethod = objClass.getMethod("get" + prefix + "AppVersion", String.class);
        Method setByOsNameMethod = objClass.getMethod("set" + prefix + "AppVersion", String.class, Integer.class);

        // Initially, it's an empty map.
        Map<String, Integer> initialMap = (Map<String, Integer>) getMapMethod.invoke(obj);
        assertTrue(initialMap.isEmpty());

        // Set the map with some null values.
        Map<String, Integer> originalMap = new HashMap<>();
        originalMap.put("fooOs", 2);
        originalMap.put("barOs", 3);
        originalMap.put("bazOs", null);
        setMapMethod.invoke(obj, originalMap);

        // Get the map back. Verify that the null value is not there.
        Map<String, Integer> gettedMap1 = (Map<String, Integer>) getMapMethod.invoke(obj);
        assertEquals(2, gettedMap1.size());
        assertEquals(2, gettedMap1.get("fooOs").intValue());
        assertEquals(3, gettedMap1.get("barOs").intValue());

        // Write to the originalMap. Get the map again and verify it's unchanged.
        originalMap.put("qwertyOs", 4);
        Map<String, Integer> gettedMap2 = (Map<String, Integer>) getMapMethod.invoke(obj);
        assertEquals(2, gettedMap2.size());
        assertEquals(2, gettedMap2.get("fooOs").intValue());
        assertEquals(3, gettedMap2.get("barOs").intValue());

        // Put a value that modifies the object's map. Verify that it appears in the new getted map, but not in the old
        // ones.
        setByOsNameMethod.invoke(obj, "asdfOs", 5);
        Map<String, Integer> gettedMap3 = (Map<String, Integer>) getMapMethod.invoke(obj);
        assertEquals(3, gettedMap3.size());
        assertEquals(2, gettedMap3.get("fooOs").intValue());
        assertEquals(3, gettedMap3.get("barOs").intValue());
        assertEquals(5, gettedMap3.get("asdfOs").intValue());

        assertEquals(2, gettedMap2.size());
        assertEquals(2, gettedMap2.get("fooOs").intValue());
        assertEquals(3, gettedMap2.get("barOs").intValue());

        assertEquals(2, gettedMap1.size());
        assertEquals(2, gettedMap1.get("fooOs").intValue());
        assertEquals(3, gettedMap1.get("barOs").intValue());

        // Test getted maps are immutable.
        try {
            gettedMap1.put("aaa", 111);
            fail("expected exception");
        } catch (RuntimeException ex) {
            // expected exception
        }

        try {
            gettedMap2.put("bbb", 222);
            fail("expected exception");
        } catch (RuntimeException ex) {
            // expected exception
        }

        try {
            gettedMap3.put("ccc", 333);
            fail("expected exception");
        } catch (RuntimeException ex) {
            // expected exception
        }

        // Test getting by OS name
        assertEquals(2, getByOsNameMethod.invoke(obj, "fooOs"));
        assertEquals(3, getByOsNameMethod.invoke(obj, "barOs"));
        assertEquals(5, getByOsNameMethod.invoke(obj, "asdfOs"));

        // We can remove by OS name
        setByOsNameMethod.invoke(obj, "asdfOs", null);
        assertNull(getByOsNameMethod.invoke(obj, "asdfOs"));
        Map<String, Integer> gettedMap4 = (Map<String, Integer>) getMapMethod.invoke(obj);
        assertEquals(2, gettedMap4.size());
        assertEquals(2, gettedMap4.get("fooOs").intValue());
        assertEquals(3, gettedMap4.get("barOs").intValue());

        // Setting the map to null converts it to an empty map. (For the setMapMethod, we need to wrap it in an array
        // because varargs with null is weird.)
        setMapMethod.invoke(obj, new Object[] { null });
        Map<String, Integer> finalMap = (Map<String, Integer>) getMapMethod.invoke(obj);
        assertTrue(finalMap.isEmpty());

        // Existing maps are unaffected.
        assertFalse(gettedMap1.isEmpty());
        assertFalse(gettedMap2.isEmpty());
        assertFalse(gettedMap3.isEmpty());
        assertFalse(gettedMap4.isEmpty());
    }
}
