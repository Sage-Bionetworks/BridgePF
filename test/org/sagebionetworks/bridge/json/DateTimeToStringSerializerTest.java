package org.sagebionetworks.bridge.json;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonGenerator;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.BridgeConstants;

public class DateTimeToStringSerializerTest {
    @Test
    public void test() throws Exception {
        // arbitrarily 2014-02-12T15:57 PST
        DateTime dateTime = new DateTime(2014, 2, 12, 15, 57, BridgeConstants.LOCAL_TIME_ZONE);
        long millis = dateTime.getMillis();

        // mock JsonGenerator
        JsonGenerator mockJGen = mock(JsonGenerator.class);

        // execute
        new DateTimeToStringSerializer().serialize(dateTime, mockJGen, null);

        // sanity check the captured serialized string
        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(mockJGen).writeString(arg.capture());
        String serialized = arg.getValue();
        assertTrue(serialized.startsWith("2014-02-12T"));

        // parse and compare milliseconds
        DateTime parsed = DateTime.parse(serialized, ISODateTimeFormat.dateTime());
        assertEquals(millis, parsed.getMillis());
    }
}
