package org.sagebionetworks.bridge.models.reports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import org.junit.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class ReportDataKeyTest {

    @Test
    public void constructParticipantKey() {
        ReportDataKey key = new ReportDataKey.Builder().withHealthCode("healthCode")
                .withIdentifier("report").withStudyIdentifier(TEST_STUDY).build();
        
        assertEquals("healthCode:report:api", key.toString());
        assertEquals("healthCode", key.getHealthCode());
        assertEquals("report", key.getIdentifier());
        assertEquals(ReportDataType.PARTICIPANT, key.getReportType());
    }
    
    @Test
    public void constructStudyKey() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier("report").withStudyIdentifier(TEST_STUDY).build();
        
        assertEquals("report:api", key.toString());
        assertNull(key.getHealthCode());
        assertEquals("report", key.getIdentifier());
        assertEquals(ReportDataType.STUDY, key.getReportType());
    }
    
    @Test
    public void canSerialize() throws Exception {
        ReportDataKey key = new ReportDataKey.Builder().withHealthCode("healthCode")
                .withIdentifier("report").withStudyIdentifier(TEST_STUDY).build();

        JsonNode node = BridgeObjectMapper.get().valueToTree(key);
        assertEquals("report", node.get("identifier").asText());
        assertEquals("participant", node.get("reportType").asText());
        assertEquals("ReportDataKey", node.get("type").asText());
        assertEquals(3, node.size()); // no healthCode, no studyId.
    }
    
    @Test
    public void cannotCreateParticipantKeyWithNullHealthCode() {
        ReportDataKey key = new ReportDataKey.Builder().withHealthCode(null).withIdentifier("report")
                .withStudyIdentifier(TEST_STUDY).build();
        
        assertEquals(ReportDataType.STUDY, key.getReportType());
    }
    
    @Test
    public void cannotCreateParticipantKeyWithEmptyHealthCode() {
        ReportDataKey key = new ReportDataKey.Builder().withHealthCode("").withIdentifier("report")
                .withStudyIdentifier(TEST_STUDY).build();
        
        assertEquals(ReportDataType.STUDY, key.getReportType());
    }
    
    @Test(expected = InvalidEntityException.class)
    public void cannotConstructKeyWithNullIdentifier() {
        new ReportDataKey.Builder().withStudyIdentifier(TEST_STUDY).build();
    }
    
    @Test(expected = InvalidEntityException.class)
    public void cannotConstructKeyWithEmptyIdentifier() {
        new ReportDataKey.Builder().withIdentifier("").withStudyIdentifier(TEST_STUDY).build();    
    }
    
}
