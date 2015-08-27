package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.joda.time.DateTime;
import org.junit.Test;

// This file exists to support a hack, but we should still test it anyway.
public class UploadUtilTest {
    @Test
    public void nullTimestamp() {
        assertNull(UploadUtil.parseIosTimestamp(null));
    }

    @Test
    public void emptyTimestamp() {
        assertNull(UploadUtil.parseIosTimestamp(""));
    }

    @Test
    public void blankTimestamp() {
        assertNull(UploadUtil.parseIosTimestamp("   "));
    }

    @Test
    public void calendarDate() {
        assertNull(UploadUtil.parseIosTimestamp("2015-08-26"));
    }

    @Test
    public void shortMalformedTimestamp() {
        assertNull(UploadUtil.parseIosTimestamp("foo"));
    }

    @Test
    public void longMalformedTimestamp() {
        assertNull(UploadUtil.parseIosTimestamp("August 26, 2015 @ 4:54:04pm PDT"));
    }

    @Test
    public void properTimestampUtc() {
        String timestampStr = "2015-08-26T23:54:04Z";
        long expectedMillis = DateTime.parse(timestampStr).getMillis();
        DateTime parsedTimestamp = UploadUtil.parseIosTimestamp(timestampStr);
        assertEquals(expectedMillis, parsedTimestamp.getMillis());
    }

    @Test
    public void properTimestampWithTimezone() {
        String timestampStr = "2015-08-26T16:54:04-07:00";
        long expectedMillis = DateTime.parse(timestampStr).getMillis();
        DateTime parsedTimestamp = UploadUtil.parseIosTimestamp(timestampStr);
        assertEquals(expectedMillis, parsedTimestamp.getMillis());
    }

    @Test
    public void iosTimestamp() {
        long expectedMillis = DateTime.parse("2015-08-26T16:54:04-07:00").getMillis();
        DateTime parsedTimestamp = UploadUtil.parseIosTimestamp("2015-08-26 16:54:04 -0700");
        assertEquals(expectedMillis, parsedTimestamp.getMillis());
    }
}
