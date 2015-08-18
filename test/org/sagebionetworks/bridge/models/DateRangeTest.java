package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;

public class DateRangeTest {
    @Test(expected = IllegalArgumentException.class)
    public void nullStartDate() {
        new DateRange(null, LocalDate.parse("2015-08-19"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullEndDate() {
        new DateRange(LocalDate.parse("2015-08-15"), null);
    }

    @Test
    public void startDateBeforeEndDate() {
        DateRange dateRange = new DateRange(LocalDate.parse("2015-08-15"), LocalDate.parse("2015-08-19"));
        assertEquals("2015-08-15", dateRange.getStartDate().toString(ISODateTimeFormat.date()));
        assertEquals("2015-08-19", dateRange.getEndDate().toString(ISODateTimeFormat.date()));
    }

    @Test
    public void startDateSameAsEndDate() {
        DateRange dateRange = new DateRange(LocalDate.parse("2015-08-17"), LocalDate.parse("2015-08-17"));
        assertEquals("2015-08-17", dateRange.getStartDate().toString(ISODateTimeFormat.date()));
        assertEquals("2015-08-17", dateRange.getEndDate().toString(ISODateTimeFormat.date()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void startDateAfterEndDate() {
        new DateRange(LocalDate.parse("2015-08-19"), LocalDate.parse("2015-08-15"));
    }

    @Test
    public void jsonSerialization() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"startDate\":\"2015-08-03\",\n" +
                "   \"endDate\":\"2015-08-07\"\n" +
                "}";

        // convert to POJO
        DateRange dateRange = BridgeObjectMapper.get().readValue(jsonText, DateRange.class);
        assertEquals("2015-08-03", dateRange.getStartDate().toString(ISODateTimeFormat.date()));
        assertEquals("2015-08-07", dateRange.getEndDate().toString(ISODateTimeFormat.date()));

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(dateRange);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(3, jsonMap.size());
        assertEquals("2015-08-03", jsonMap.get("startDate"));
        assertEquals("2015-08-07", jsonMap.get("endDate"));
        assertEquals("DateRange", jsonMap.get("type"));
    }
}
