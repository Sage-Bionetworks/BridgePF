package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.LocalDate;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.services.HealthDataService;

public class UploadArtifactsHandlerTest {
    private static final long ARBITRARY_TIMESTAMP = 1424136378727L;
    private static final String TEST_UPLOAD_ID = "test-upload";

    @Test
    public void test() throws Exception {
        // mock health data service
        HealthDataService mockHealthDataService = mock(HealthDataService.class);
        when(mockHealthDataService.createOrUpdateRecord(any())).thenAnswer(invocation -> invocation.getArgumentAt(
                0, HealthDataRecord.class).getId());

        // set up handler
        UploadArtifactsHandler handler = new UploadArtifactsHandler();
        handler.setHealthDataService(mockHealthDataService);

        // Make record. Attachments are handled earlier in the call chain, and the data JSON node just contains the S3
        // filename of the attachment.
        String dataJsonText = "{\n" +
                "   \"json.json.string\":\"This is a string\",\n" +
                "   \"json.json.int\":42,\n" +
                "   \"json.json.attachment\":\"" + TEST_UPLOAD_ID + "-json.json.attachment\"\n" +
                "}";
        JsonNode dataJson = BridgeObjectMapper.get().readTree(dataJsonText);
        HealthDataRecord record = createValidRecord(dataJson);

        // only need upload ID from upload
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);

        // set up context
        UploadValidationContext context = new UploadValidationContext();
        context.setHealthDataRecord(record);
        context.setUpload(upload);

        // execute
        handler.handle(context);

        // Validate result. Record ID equal to upload ID is the most important. The rest are just copied.
        ArgumentCaptor<HealthDataRecord> createdRecordCaptor = ArgumentCaptor.forClass(HealthDataRecord.class);
        verify(mockHealthDataService).createOrUpdateRecord(createdRecordCaptor.capture());

        HealthDataRecord createdRecord = createdRecordCaptor.getValue();
        assertEquals(TEST_UPLOAD_ID, createdRecord.getId());
        assertSame(record, createdRecord);

        // validate record ID in the context
        assertEquals(TEST_UPLOAD_ID, context.getRecordId());

        // validate no messages on the context
        assertTrue(context.getMessageList().isEmpty());
    }

    // creates a record that has all the valid values filled in, with the data JsonNode specified
    private static HealthDataRecord createValidRecord(JsonNode dataNode) {
        // none of these values matter (except data, which is specified), so just fill in whatever
        // All that matters is that the values are written to DDB.
        HealthDataRecord record = HealthDataRecord.create();
        record.setCreatedOn(ARBITRARY_TIMESTAMP);
        record.setData(dataNode);
        record.setHealthCode("dummy-healthcode");
        record.setMetadata(BridgeObjectMapper.get().createObjectNode());
        record.setSchemaId("dummy-schema");
        record.setSchemaRevision(1);
        record.setStudyId("dummy-study");
        record.setUploadDate(LocalDate.parse("2015-11-18"));
        record.setUploadId(TEST_UPLOAD_ID);
        record.setUserExternalId("dummy-external-ID");
        record.setUserSharingScope(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS);
        record.setUserDataGroups(TestConstants.USER_DATA_GROUPS);
        record.setVersion(42L);
        return record;
    }
}
