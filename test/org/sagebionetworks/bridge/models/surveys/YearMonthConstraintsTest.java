package org.sagebionetworks.bridge.models.surveys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import org.joda.time.YearMonth;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class YearMonthConstraintsTest {
    @Test
    public void canSerializeCorrectly() throws Exception {
        YearMonthConstraints constraints = new YearMonthConstraints();
        constraints.setEarliestValue(YearMonth.parse("2000-01"));
        constraints.setLatestValue(YearMonth.parse("2009-12"));
        constraints.setAllowFuture(true);
        
        String json = BridgeObjectMapper.get().writeValueAsString(constraints);
        JsonNode node = BridgeObjectMapper.get().readTree(json);

        assertEquals("2000-01", node.get("earliestValue").textValue());
        assertEquals("2009-12", node.get("latestValue").textValue());
        assertEquals("yearmonth", node.get("dataType").textValue());
        assertTrue(node.get("allowFuture").booleanValue());
        assertEquals("YearMonthConstraints", node.get("type").textValue());
        
        YearMonthConstraints deser = BridgeObjectMapper.get().readValue(node.toString(), YearMonthConstraints.class);
        assertEquals(YearMonth.parse("2000-01"), deser.getEarliestValue());
        assertEquals(YearMonth.parse("2009-12"), deser.getLatestValue());
        assertTrue(deser.getAllowFuture());
        assertEquals(DataType.YEARMONTH, deser.getDataType());
        assertEquals(EnumSet.of(UIHint.YEARMONTH), deser.getSupportedHints());
    }
}
