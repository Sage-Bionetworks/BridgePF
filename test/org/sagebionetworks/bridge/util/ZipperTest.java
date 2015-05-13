package org.sagebionetworks.bridge.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class ZipperTest {

    @Test
    public void testZipUnzip() throws Exception {
        Zipper zipper = new Zipper(10, 20);
        zipUnzip(zipper, 20);
    }

    @Test(expected=ZipOverflowException.class)
    public void testTooManyZipEntries() throws Exception {
        Zipper zipper = new Zipper(10, 20);
        zipUnzip(zipper, 21);
    }

    @Test(expected=ZipOverflowException.class)
    public void testZipEntryTooBig() throws Exception {
        Zipper zipper = new Zipper(2, 20);
        Map<String, byte[]> dataMap = new HashMap<>();
        dataMap.put("testZipEntryTooBig", "aaa".getBytes());
        byte[] zipped = zipper.zip(dataMap);
        zipper.unzip(zipped);
    }

    private void zipUnzip(final Zipper zipper, final int numEntries) throws Exception {
        Map<String, byte[]> dataMap = new HashMap<>();
        for (int i = 0; i < numEntries; i++) {
            dataMap.put(Integer.toString(i), Integer.toString(i).getBytes());
        }
        byte[] zipped = zipper.zip(dataMap);
        dataMap = zipper.unzip(zipped);
        assertNotNull(dataMap);
        assertEquals(numEntries, dataMap.size());
        for (int i = 0; i < numEntries; i++) {
            byte[] unzipped = dataMap.get(Integer.toString(i));
            assertEquals(Integer.toString(i), new String(unzipped));
        }
    }
}
