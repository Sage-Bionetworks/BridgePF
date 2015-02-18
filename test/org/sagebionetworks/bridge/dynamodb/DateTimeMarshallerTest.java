package org.sagebionetworks.bridge.dynamodb;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.BridgeConstants;

public class DateTimeMarshallerTest {
    private static final DateTimeMarshaller MARSHALLER = new DateTimeMarshaller();

    @Test
    public void testMarshalling() {
        // arbitrarily 2014-02-12T15:04 PST
        DateTime dateTime = new DateTime(2014, 2, 12, 15, 4, BridgeConstants.LOCAL_TIME_ZONE);
        long millis = dateTime.getMillis();

        // marshall and sanity check
        String marshalled = MARSHALLER.marshall(dateTime);
        assertTrue(marshalled.startsWith("2014-02-12T"));

        // unmarshal and validate
        DateTime unmarshalled = MARSHALLER.unmarshall(DateTime.class, marshalled);
        assertEquals(millis, unmarshalled.getMillis());
    }
}
