package org.sagebionetworks.bridge.models.reports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
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
    public void canIncludeDateValidation() {
        try {
            // This is tested in tests for the validator, but date is actually validated in the 
            // build method.
            new ReportDataKey.Builder().validateWithDate(null).build();
        } catch(InvalidEntityException e) {
            assertEquals("date is required", e.getErrors().get("date").get(0));
            // integrated alongside Validator errors
            assertEquals("identifier cannot be missing or blank", e.getErrors().get("identifier").get(0));
        }
    }
    
    @Test
    public void constructStudyKey() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY).withStudyIdentifier(TEST_STUDY)
                .withIdentifier("report").validateWithDate(LocalDate.parse("2012-02-02")).build();
        
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
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.PARTICIPANT).withStudyIdentifier(TEST_STUDY)
                .withHealthCode("AAA")
                .withIdentifier("report").build();
        
        // This was constructed, the date is valid. It's not part of the key, it's validated in the builder
        // so validation errors are combined with key validation errors.
        assertEquals("AAA:report:api", key.getKeyString());
        assertEquals("api:PARTICIPANT", key.getIndexKeyString());
        assertEquals("AAA", key.getHealthCode());
        assertEquals("report", key.getIdentifier());
        assertEquals(ReportType.PARTICIPANT, key.getReportType());
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
