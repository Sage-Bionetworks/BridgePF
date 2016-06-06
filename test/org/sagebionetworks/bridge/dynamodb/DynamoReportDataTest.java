package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    private static final LocalDate DATE = LocalDate.parse("2015-02-20");

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
        reportData.setDate(DATE);
        reportData.setData(objNode);
        
        String json = MAPPER.writeValueAsString(reportData);
        
        JsonNode node = MAPPER.readTree(json);
        assertNull(node.get("key"));
        assertEquals(DATE.toString(), node.get("date").asText());
        assertTrue(node.get("data").get("a").asBoolean());
        assertEquals("string", node.get("data").get("b").asText());
        assertEquals(10, node.get("data").get("c").asInt());
        
        ReportData deser = MAPPER.readValue(json, ReportData.class);
        assertNull(deser.getKey());
        assertEquals(DATE, deser.getDate());
        assertTrue(deser.getData().get("a").asBoolean());
        assertEquals("string", deser.getData().get("b").asText());
        assertEquals(10, deser.getData().get("c").asInt());
    }
}
