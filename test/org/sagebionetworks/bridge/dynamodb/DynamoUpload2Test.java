package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class DynamoUpload2Test {
    @Test
    public void testGetSetValidationMessageList() {
        DynamoUpload2 upload2 = new DynamoUpload2();

        // initial get gives empty list
        List<String> initialList = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        // set and validate
        upload2.setValidationMessageList(ImmutableList.of("first message"));
        List<String> list2 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        // set should overwrite
        upload2.setValidationMessageList(ImmutableList.of("second message"));
        List<String> list3 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        assertEquals(1, list3.size());
        assertEquals("second message", list3.get(0));
    }

    @Test
    public void testGetAppendValidationMessageList() {
        DynamoUpload2 upload2 = new DynamoUpload2();

        // initial get gives empty list
        List<String> initialList = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        // append and validate
        upload2.appendValidationMessages(ImmutableList.of("first message"));
        List<String> list2 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        // append again
        upload2.appendValidationMessages(ImmutableList.of("second message"));
        List<String> list3 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        assertEquals(2, list3.size());
        assertEquals("first message", list3.get(0));
        assertEquals("second message", list3.get(1));
    }

    @Test
    public void testGetSetAppendValidationMessageList() {
        DynamoUpload2 upload2 = new DynamoUpload2();

        // initial get gives empty list
        List<String> initialList = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        // set on an empty list
        upload2.setValidationMessageList(ImmutableList.of("first message"));
        List<String> list2 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        // append on a set
        upload2.appendValidationMessages(ImmutableList.of("second message"));
        List<String> list3 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        assertEquals(2, list3.size());
        assertEquals("first message", list3.get(0));
        assertEquals("second message", list3.get(1));

        // set should overwrite the append
        upload2.setValidationMessageList(ImmutableList.of("third message"));
        List<String> list4 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        assertEquals(2, list3.size());
        assertEquals("first message", list3.get(0));
        assertEquals("second message", list3.get(1));

        assertEquals(1, list4.size());
        assertEquals("third message", list4.get(0));
    }
}
