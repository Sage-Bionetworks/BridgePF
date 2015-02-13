package org.sagebionetworks.bridge.json;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParser;
import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.BridgeConstants;

public class JodaDateTimeDeserializerTest {
    @Test
    public void test() throws Exception {
        // arbitrarily 2014-02-12T16:07 PST
        long expectedMillis = new DateTime(2014, 2, 12, 16, 7, BridgeConstants.LOCAL_TIME_ZONE).getMillis();

        // mock JsonParser
        JsonParser mockJP = mock(JsonParser.class);
        when(mockJP.getText()).thenReturn("2014-02-12T16:07-0800");

        // execute and validate
        DateTime result = new JodaDateTimeDeserializer().deserialize(mockJP, null);
        assertEquals(expectedMillis, result.getMillis());
    }
}
