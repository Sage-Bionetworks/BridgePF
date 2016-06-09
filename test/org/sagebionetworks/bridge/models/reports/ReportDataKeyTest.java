package org.sagebionetworks.bridge.models.reports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ReportDataKeyTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(ReportDataKey.class).allFieldsShouldBeUsed().verify();
    }    
    
    @Test
    public void constructParticipantKey() {
        ReportDataKey key = new ReportDataKey.Builder().withHealthCode("healthCode").withStudyIdentifier(TEST_STUDY)
                .withReportType(ReportType.PARTICIPANT).withIdentifier("report").build();

        assertEquals("healthCode:report:api", key.getKeyString());
        assertEquals("api:PARTICIPANT", key.getIndexKeyString());
        assertEquals("healthCode", key.getHealthCode());
        assertEquals("report", key.getIdentifier());
        assertEquals(ReportType.PARTICIPANT, key.getReportType());
    }
    
    @Test
    public void constructStudyKey() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY).withStudyIdentifier(TEST_STUDY).withIdentifier("report").build();
        
        assertEquals("report:api", key.getKeyString());
        assertEquals("api:STUDY", key.getIndexKeyString());
        assertNull(key.getHealthCode());
        assertEquals("report", key.getIdentifier());
        assertEquals(ReportType.STUDY, key.getReportType());
    }
    
    @Test
    public void canSerialize() throws Exception {
        ReportDataKey key = new ReportDataKey.Builder().withHealthCode("healthCode").withStudyIdentifier(TEST_STUDY)
                .withReportType(ReportType.PARTICIPANT).withIdentifier("report").build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(key);
        assertEquals("report", node.get("identifier").asText());
        assertEquals("participant", node.get("reportType").asText());
        assertEquals("ReportDataKey", node.get("type").asText());
        assertEquals(3, node.size()); // no healthCode, no studyId.
    }
    
    // Validator test verify the key cannot be constructed in an invalid state.
}
