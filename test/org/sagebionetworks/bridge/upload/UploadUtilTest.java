package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

@SuppressWarnings("ConstantConditions")
public class UploadUtilTest {
    @Test
    public void canonicalize() throws Exception {
        // { inputNode, fieldType, expectedNode, expectedErrorMessage, expectedIsValid }
        Object[][] testCaseArray = {
                // java null
                { null, UploadFieldType.BOOLEAN, null, true },
                // json null
                { NullNode.instance, UploadFieldType.BOOLEAN, NullNode.instance, true },
                // attachment blob
                { new TextNode("dummy attachment"), UploadFieldType.ATTACHMENT_BLOB, new TextNode("dummy attachment"),
                        true },
                // attachment csv
                { new TextNode("dummy attachment"), UploadFieldType.ATTACHMENT_CSV, new TextNode("dummy attachment"),
                        true },
                // attachment json blob
                { new TextNode("dummy attachment"), UploadFieldType.ATTACHMENT_JSON_BLOB,
                        new TextNode("dummy attachment"), true },
                // attachment json table
                { new TextNode("dummy attachment"), UploadFieldType.ATTACHMENT_JSON_TABLE,
                        new TextNode("dummy attachment"), true },
                // attachment v2
                { new TextNode("dummy attachment"), UploadFieldType.ATTACHMENT_V2, new TextNode("dummy attachment"),
                        true },
                // inline json
                { new TextNode("dummy inline JSON"), UploadFieldType.INLINE_JSON_BLOB,
                        new TextNode("dummy inline JSON"), true },
                // boolean zero false
                { new IntNode(0), UploadFieldType.BOOLEAN, BooleanNode.FALSE, true },
                // boolean positive true
                { new IntNode(3), UploadFieldType.BOOLEAN, BooleanNode.TRUE, true },
                // boolean negative true
                { new IntNode(-3), UploadFieldType.BOOLEAN, BooleanNode.TRUE, true },
                // boolean string false
                { new TextNode("false"), UploadFieldType.BOOLEAN, BooleanNode.FALSE, true },
                // boolean mixed case string false
                { new TextNode("fALSE"), UploadFieldType.BOOLEAN, BooleanNode.FALSE, true },
                // boolean string true
                { new TextNode("true"), UploadFieldType.BOOLEAN, BooleanNode.TRUE, true },
                // boolean mixed case string true
                { new TextNode("TrUe"), UploadFieldType.BOOLEAN, BooleanNode.TRUE, true },
                // boolean empty string
                { new TextNode(""), UploadFieldType.BOOLEAN, null, false },
                // boolean invalid string
                { new TextNode("Yes"), UploadFieldType.BOOLEAN, null, false },
                // boolean boolean false
                { BooleanNode.FALSE, UploadFieldType.BOOLEAN, BooleanNode.FALSE, true },
                // boolean boolean true
                { BooleanNode.TRUE, UploadFieldType.BOOLEAN, BooleanNode.TRUE, true },
                // boolean invalid type
                { new DoubleNode(3.14), UploadFieldType.BOOLEAN, null, false },
                // calendar date invalid type
                { new IntNode(1234), UploadFieldType.CALENDAR_DATE, null, false },
                // calendar date empty string
                { new TextNode(""), UploadFieldType.CALENDAR_DATE, null, false },
                // calendar date invalid string
                { new TextNode("June 1, 2016"), UploadFieldType.CALENDAR_DATE, null, false },
                // calendar date valid
                { new TextNode("2016-06-01"), UploadFieldType.CALENDAR_DATE, new TextNode("2016-06-01"), true },
                // calendar date full datetime
                { new TextNode("2016-06-01T11:00Z"), UploadFieldType.CALENDAR_DATE, new TextNode("2016-06-01"), true },
                // duration invalid type
                { new TextNode("2016-06-01"), UploadFieldType.CALENDAR_DATE, new TextNode("2016-06-01"), true },
                // duration empty string
                { new TextNode(""), UploadFieldType.DURATION_V2, null, false },
                // duration invalid string
                { new TextNode("one hour"), UploadFieldType.DURATION_V2, null, false },
                // duration valid
                { new TextNode("PT1H"), UploadFieldType.DURATION_V2, new TextNode("PT1H"), true },
                // float valid
                { new DecimalNode(new BigDecimal("3.14")), UploadFieldType.FLOAT,
                        new DecimalNode(new BigDecimal("3.14")), true },
                // float from int
                { new IntNode(42), UploadFieldType.FLOAT, new IntNode(42), true },
                // float from string
                { new TextNode("2.718"), UploadFieldType.FLOAT, new DecimalNode(new BigDecimal("2.718")), true },
                // float from int string
                { new TextNode("13"), UploadFieldType.FLOAT, new DecimalNode(new BigDecimal("13")), true },
                // float empty string
                { new TextNode(""), UploadFieldType.FLOAT, null, false },
                // float invalid string
                { new TextNode("three point one four"), UploadFieldType.FLOAT, null, false },
                // float invalid type
                { BooleanNode.FALSE, UploadFieldType.FLOAT, null, false },
                // int valid
                { new IntNode(57), UploadFieldType.INT, new IntNode(57), true },
                // int from float
                { new DoubleNode(3.14), UploadFieldType.INT, new BigIntegerNode(new BigInteger("3")), true },
                // int from string
                { new TextNode("1234"), UploadFieldType.INT, new BigIntegerNode(new BigInteger("1234")), true },
                // int from float string
                { new TextNode("12.34"), UploadFieldType.INT, new BigIntegerNode(new BigInteger("12")), true },
                // int empty string
                { new TextNode(""), UploadFieldType.INT, null, false },
                // int invalid string
                { new TextNode("twelve"), UploadFieldType.INT, null, false },
                // int invalid type
                { BooleanNode.TRUE, UploadFieldType.INT, null, false },
                // multi-choice not array
                { new TextNode("[3, 5, 7]"), UploadFieldType.MULTI_CHOICE, null, false },
                // multi-choice valid (some elements aren't strings)
                { BridgeObjectMapper.get().readTree("[true, false, \"Don't Know\"]"), UploadFieldType.MULTI_CHOICE,
                        BridgeObjectMapper.get().readTree("[\"true\", \"false\", \"Don_t Know\"]"), true },
                // multi-choice with sanitizing answers
                { BridgeObjectMapper.get().readTree("[\".foo..bar\", \"$baz\"]"), UploadFieldType.MULTI_CHOICE,
                        BridgeObjectMapper.get().readTree("[\"_foo.bar\", \"_baz\"]"), true },
                // single-choice array
                { BridgeObjectMapper.get().readTree("[\"football\"]"), UploadFieldType.SINGLE_CHOICE,
                        new TextNode("football"), true },
                // single-choice empty array
                { BridgeObjectMapper.get().readTree("[]"), UploadFieldType.SINGLE_CHOICE, null, false },
                // single-choice multi array
                { BridgeObjectMapper.get().readTree("[\"foo\", \"bar\"]"), UploadFieldType.SINGLE_CHOICE, null,
                        false },
                // single-choice array non-string
                { BridgeObjectMapper.get().readTree("[42]"), UploadFieldType.SINGLE_CHOICE, new TextNode("42"), true },
                // single-choice string
                { new TextNode("swimming"), UploadFieldType.SINGLE_CHOICE, new TextNode("swimming"), true },
                // single-choice other type
                { new IntNode(23), UploadFieldType.SINGLE_CHOICE, new TextNode("23"), true },
                // string valid
                { new TextNode("Other"), UploadFieldType.STRING, new TextNode("Other"), true },
                // string from non-string
                { new IntNode(1337), UploadFieldType.STRING, new TextNode("1337"), true },
                // local time invalid type
                { new IntNode(2300), UploadFieldType.TIME_V2, null, false },
                // local time empty string
                { new TextNode(""), UploadFieldType.TIME_V2, null, false },
                // local time invalid string
                { new TextNode("11pm"), UploadFieldType.TIME_V2, null, false },
                // local time valid
                { new TextNode("12:34:56.789"), UploadFieldType.TIME_V2, new TextNode("12:34:56.789"), true },
                // local time full datetime
                { new TextNode("2016-06-01T12:34:56.789-0700"), UploadFieldType.TIME_V2, new TextNode("12:34:56.789"),
                        true },
                // datetime invalid type
                { BooleanNode.TRUE, UploadFieldType.TIMESTAMP, null, false },
                // datetime number
                { new LongNode(1464825450123L), UploadFieldType.TIMESTAMP, new TextNode("2016-06-01T23:57:30.123Z"),
                        true },
                // datetime empty string
                { new TextNode(""), UploadFieldType.TIMESTAMP, null, false },
                // datetime invalid string
                { new TextNode("Jun 1 2016 11:59pm"), UploadFieldType.TIMESTAMP, null, false },
                // datetime valid
                { new TextNode("2016-06-01T23:59:59.999Z"), UploadFieldType.TIMESTAMP,
                        new TextNode("2016-06-01T23:59:59.999Z"), true },
        };

        for (Object[] oneTestCase : testCaseArray) {
            JsonNode inputNode = (JsonNode) oneTestCase[0];
            String inputNodeStr = BridgeObjectMapper.get().writeValueAsString(inputNode);
            UploadFieldType fieldType = (UploadFieldType) oneTestCase[1];

            CanonicalizationResult result = UploadUtil.canonicalize(inputNode, fieldType);
            assertEquals("Test case: " + inputNodeStr + " as " + fieldType.name(), oneTestCase[2],
                    result.getCanonicalizedValueNode());

            boolean expectedIsValid = (boolean) oneTestCase[3];
            if (expectedIsValid) {
                assertTrue("Test case: " + inputNodeStr + " as " + fieldType.name(), result.isValid());
                assertNull("Test case: " + inputNodeStr + " as " + fieldType.name(), result.getErrorMessage());
            } else {
                assertFalse("Test case: " + inputNodeStr + " as " + fieldType.name(), result.isValid());
                assertNotNull("Test case: " + inputNodeStr + " as " + fieldType.name(), result.getErrorMessage());
            }
        }
    }

    @Test
    public void convertToStringNode() throws Exception {
        // java null
        {
            JsonNode result = UploadUtil.convertToStringNode(null);
            assertNull(result);
        }

        // json null
        {
            JsonNode result = UploadUtil.convertToStringNode(NullNode.instance);
            assertTrue(result.isNull());
        }

        // { inputNode, outputString }
        Object[][] testCaseArray = {
                // not a string
                { new IntNode(42), "42" },
                // empty string
                { new TextNode(""), "" },
                // is a string
                { new TextNode("foobarbaz"), "foobarbaz" },
        };

        for (Object[] oneTestCase : testCaseArray) {
            JsonNode inputNode = (JsonNode) oneTestCase[0];
            String inputNodeStr = BridgeObjectMapper.get().writeValueAsString(inputNode);

            JsonNode result = UploadUtil.convertToStringNode(inputNode);
            assertTrue("Test case: " + inputNodeStr, result.isTextual());
            assertEquals("Test case: " + inputNodeStr, oneTestCase[1], result.textValue());
        }

        // arbitrary JSON object
        {
            JsonNode inputNode = BridgeObjectMapper.get().readTree("{\"key\":\"value\"}");
            JsonNode result = UploadUtil.convertToStringNode(inputNode);
            assertTrue(result.isTextual());

            // We don't want to couple to a specific way of JSON formatting. So instead of string checking, convert the
            // string back into JSON and compare JSON directly.
            JsonNode resultNestedJson = BridgeObjectMapper.get().readTree(result.textValue());
            assertEquals(inputNode, resultNestedJson);
        }
    }

    @Test
    public void getJsonNodeAsString() throws Exception {
        // { inputNode, expectedOutputString }
        Object[][] testCaseArray = {
                // java null
                { null, null },
                // json null
                { NullNode.instance, null },
                // not a string
                { BooleanNode.FALSE, "false" },
                // empty string
                { new TextNode(""), "" },
                // is a string
                { new TextNode("my string"), "my string" },
        };

        for (Object[] oneTestCase : testCaseArray) {
            JsonNode inputNode = (JsonNode) oneTestCase[0];
            String inputNodeStr = BridgeObjectMapper.get().writeValueAsString(inputNode);

            String retVal = UploadUtil.getAsString(inputNode);
            assertEquals("Test case: " + inputNodeStr, oneTestCase[1], retVal);
        }

        // arbitrary JSON object
        {
            JsonNode inputNode = BridgeObjectMapper.get().readTree("{\"key\":\"value\"}");
            String retVal = UploadUtil.getAsString(inputNode);

            // We don't want to couple to a specific way of JSON formatting. So instead of string checking, convert the
            // string back into JSON and compare JSON directly.
            JsonNode resultNestedJson = BridgeObjectMapper.get().readTree(retVal);
            assertEquals(inputNode, resultNestedJson);
        }
    }

    @Test
    public void isValidFieldNameInvalid() {
        String[] testCases = {
                null,
                "",
                "   ",
                "_foo",
                "foo_",
                "-foo",
                "foo-",
                ".foo",
                "foo.",
                ".foo",
                "foo.",
                "foo*bar",
                "foo__bar",
                "foo--bar",
                "foo..bar",
                "foo  bar",
                "foo-_-bar",
                "select",
                "where",
                "time",
        };

        for (String oneTestCase : testCases) {
            assertFalse(oneTestCase + " should be invalid", UploadUtil.isValidSchemaFieldName(oneTestCase));
        }
    }

    @Test
    public void isValidFieldNameValid() {
        String[] testCases = {
                "foo",
                "foo_bar",
                "foo-bar",
                "foo.bar",
                "foo bar",
                "foo-bar_baz.qwerty asdf",
        };

        for (String oneTestCase : testCases) {
            assertTrue(oneTestCase + " should be valid", UploadUtil.isValidSchemaFieldName(oneTestCase));
        }
    }

    @Test
    public void nullCalendarDate() {
        assertNull(UploadUtil.parseIosCalendarDate(null));
    }

    @Test
    public void emptyCalendarDate() {
        assertNull(UploadUtil.parseIosCalendarDate(""));
    }

    @Test
    public void blankCalendarDate() {
        assertNull(UploadUtil.parseIosCalendarDate("   "));
    }

    @Test
    public void shortMalformedCalendarDate() {
        assertNull(UploadUtil.parseIosCalendarDate("Xmas2015"));
    }

    @Test
    public void longMalformedCalendarDate() {
        assertNull(UploadUtil.parseIosCalendarDate("December 25 2015"));
    }

    @Test
    public void validCalendarDate() {
        LocalDate date = UploadUtil.parseIosCalendarDate("2015-12-25");
        assertEquals(2015, date.getYear());
        assertEquals(12, date.getMonthOfYear());
        assertEquals(25, date.getDayOfMonth());
    }

    @Test
    public void timestampCalendarDate() {
        LocalDate date = UploadUtil.parseIosCalendarDate("2015-12-25T14:33-0800");
        assertEquals(2015, date.getYear());
        assertEquals(12, date.getMonthOfYear());
        assertEquals(25, date.getDayOfMonth());
    }

    @Test
    public void truncatesIntoValidCalendarDate() {
        LocalDate date = UploadUtil.parseIosCalendarDate("2015-12-25 @ lunchtime");
        assertEquals(2015, date.getYear());
        assertEquals(12, date.getMonthOfYear());
        assertEquals(25, date.getDayOfMonth());
    }

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
