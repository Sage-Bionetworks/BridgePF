package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DynamoReportDataTest {
    
    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    private static final DateTime DATETIME = DateTime.parse("2015-02-20T16:32:12.123-05:00");

    @Test
    public void canSerialize() throws Exception {
        DynamoReportData reportData = new DynamoReportData();
        
        ReportDataKey key = new ReportDataKey.Builder().withHealthCode("ABC")
                .withIdentifier("foo").withReportType(ReportType.PARTICIPANT)
                .withStudyIdentifier(TestConstants.TEST_STUDY).build();
        
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("a", true);
        objNode.put("b", "string");
        objNode.put("c", 10);
        
        reportData.setKey(key.getKeyString());
        reportData.setDateTime(DATETIME);
        reportData.setData(objNode);
        reportData.setSubstudyIds(TestConstants.USER_SUBSTUDY_IDS);
        
        String json = MAPPER.writeValueAsString(reportData);
        
        JsonNode node = MAPPER.readTree(json);
        assertNull(node.get("key"));
        assertEquals(DATETIME.toString(), node.get("date").textValue());
        assertEquals(DATETIME.toString(), node.get("dateTime").textValue());
        assertTrue(node.get("data").get("a").booleanValue());
        assertEquals("string", node.get("data").get("b").textValue());
        assertEquals(10, node.get("data").get("c").intValue());
        assertEquals("substudyA", node.get("substudyIds").get(0).textValue());
        assertEquals("substudyB", node.get("substudyIds").get(1).textValue());
        assertEquals("ReportData", node.get("type").textValue());
        assertEquals(5, node.size());
        
        ReportData deser = MAPPER.readValue(json, ReportData.class);
        assertNull(deser.getKey());
        assertEquals(DATETIME, deser.getDateTime());
        assertEquals(DATETIME.toString(), deser.getDate());
        assertTrue(deser.getData().get("a").asBoolean());
        assertEquals("string", deser.getData().get("b").asText());
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, deser.getSubstudyIds());
        assertEquals(10, deser.getData().get("c").asInt());
    }
    
    @Test
    public void canSetEitherLocalDateOrDateTime() throws Exception {
        DateTime dateTime = DateTime.parse("2016-10-10T10:42:42.123-07:00");
        LocalDate localDate = LocalDate.parse("2016-10-10");
        
        DynamoReportData report = new DynamoReportData();
        report.setLocalDate(localDate);
        assertEquals(localDate.toString(), report.getDate());
        assertEquals(localDate, report.getLocalDate());
        assertNull(report.getDateTime());
        
        report = new DynamoReportData();
        report.setDateTime(dateTime);
        assertEquals(dateTime.toString(), report.getDate());
        assertEquals(dateTime, report.getDateTime());
        assertNull(report.getLocalDate());
    }
}
