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
                .withReportType(ReportType.STUDY).withStudyIdentifier(TEST_STUDY)
                .withIdentifier("report").build();
        
        // This was constructed, the date is valid. It's not part of the key, it's validated in the builder
        // so validation errors are combined with key validation errors.
        assertEquals("report:api", key.getKeyString());
        assertEquals("api:STUDY", key.getIndexKeyString());
        assertNull(key.getHealthCode());
        assertEquals("report", key.getIdentifier());
        assertEquals(ReportType.STUDY, key.getReportType());
    }

    @Test
    public void canConstructKeyWithoutValidatingDate() {
        ReportDataKey key = new ReportDataKey.Builder().withReportType(ReportType.PARTICIPANT)
                .withStudyIdentifier(TEST_STUDY).withHealthCode("AAA").withIdentifier("report").build();
        
        assertEquals("AAA:report:api", key.getKeyString());
        assertEquals("api:PARTICIPANT", key.getIndexKeyString());
        assertEquals("AAA", key.getHealthCode());
        assertEquals("report", key.getIdentifier());
        assertEquals(ReportType.PARTICIPANT, key.getReportType());
    }
    
    @Test
    public void canSerialize() throws Exception {
        // NOTE: Although we use @JsonIgnore annotations, we never serialize this value and return it via the API,
        // so arguably none of this is necessary.
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
