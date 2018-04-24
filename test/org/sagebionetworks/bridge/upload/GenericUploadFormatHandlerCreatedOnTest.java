package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

public class GenericUploadFormatHandlerCreatedOnTest {
    private static final String CREATED_ON_STRING = "2017-09-26T01:10:21.173+0900";
    private static final long CREATED_ON_MILLIS = DateUtils.convertToMillisFromEpoch(CREATED_ON_STRING);
    private static final String CREATED_ON_TIMEZONE = "+0900";
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2017-09-27T13:39:01.642-0700").getMillis();

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void noCreatedOn() {
        // Context and record are write-only in parseCreatedOnToRecord().
        UploadValidationContext context = new UploadValidationContext();
        HealthDataRecord record = HealthDataRecord.create();

        // info.json has no createdOn
        ObjectNode infoJsonNode = BridgeObjectMapper.get().createObjectNode();

        // execute
        GenericUploadFormatHandler.parseCreatedOnToRecord(context, infoJsonNode, record);

        // createdOn is MOCK_NOW_MILLIS with no timezone
        assertEquals(MOCK_NOW_MILLIS, record.getCreatedOn().longValue());
        assertNull(record.getCreatedOnTimeZone());

        // one message
        assertEquals(1, context.getMessageList().size());
        assertEquals("Upload has no createdOn; using current time.", context.getMessageList().get(0));
    }

    @Test
    public void invalidCreatedOn() {
        // Context and record are write-only in parseCreatedOnToRecord().
        UploadValidationContext context = new UploadValidationContext();
        HealthDataRecord record = HealthDataRecord.create();

        // info.json has createdOn with a invalid format
        ObjectNode infoJsonNode = BridgeObjectMapper.get().createObjectNode();
        infoJsonNode.put(UploadUtil.FIELD_CREATED_ON, "Tuesday morning");

        // execute
        GenericUploadFormatHandler.parseCreatedOnToRecord(context, infoJsonNode, record);

        // createdOn is MOCK_NOW_MILLIS with no timezone
        assertEquals(MOCK_NOW_MILLIS, record.getCreatedOn().longValue());
        assertNull(record.getCreatedOnTimeZone());

        // two messages
        assertEquals(2, context.getMessageList().size());
        assertEquals("Invalid date-time: Tuesday morning", context.getMessageList().get(0));
        assertEquals("Upload has no createdOn; using current time.", context.getMessageList().get(1));
    }

    @Test
    public void withCreatedOn() {
        // Context and record are write-only in parseCreatedOnToRecord().
        UploadValidationContext context = new UploadValidationContext();
        HealthDataRecord record = HealthDataRecord.create();

        // info.json with createdOn
        ObjectNode infoJsonNode = BridgeObjectMapper.get().createObjectNode();
        infoJsonNode.put(UploadUtil.FIELD_CREATED_ON, CREATED_ON_STRING);

        // execute
        GenericUploadFormatHandler.parseCreatedOnToRecord(context, infoJsonNode, record);

        // validate createdOn and timeZone
        assertEquals(CREATED_ON_MILLIS, record.getCreatedOn().longValue());
        assertEquals(CREATED_ON_TIMEZONE, record.getCreatedOnTimeZone());

        // no messages
        assertTrue(context.getMessageList().isEmpty());
    }
}
