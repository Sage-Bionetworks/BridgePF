package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;

import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataAttachment;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.services.HealthDataService;

public class UploadArtifactsHandlerTest {
    private static final long ARBITRARY_TIMESTAMP = 1424136378727L;
    private static final String ATTACHMENT_ID_BAR = "attachment-bar";
    private static final String ATTACHMENT_ID_FOO = "attachment-foo";
    private static final String ATTACHMENT_TEXT_BAR = "This is bar";
    private static final String ATTACHMENT_TEXT_FOO = "This is foo";
    private static final byte[] BYTES_BAR = ATTACHMENT_TEXT_BAR.getBytes(Charsets.UTF_8);
    private static final byte[] BYTES_FOO = ATTACHMENT_TEXT_FOO.getBytes(Charsets.UTF_8);
    private static final String TEST_RECORD_ID = "test-record";
    private static final String TEST_UPLOAD_ID = "test-upload";

    @Test
    public void test() throws Exception {
        // Intermediate record w/o attachments.
        String dataJsonText = "{\n" +
                "   \"json.json.string\":\"This is a string\",\n" +
                "   \"json.json.int\":42\n" +
                "}";
        JsonNode dataJson = BridgeObjectMapper.get().readTree(dataJsonText);
        HealthDataRecord intermediateRecord = createValidRecordBuilder(dataJson).withId(TEST_RECORD_ID).build();

        // mock health data service
        HealthDataService mockHealthDataService = mock(HealthDataService.class);

        ArgumentCaptor<HealthDataRecord> createRecordArgCaptor = ArgumentCaptor.forClass(HealthDataRecord.class);
        when(mockHealthDataService.createOrUpdateRecord(createRecordArgCaptor.capture())).thenReturn(TEST_RECORD_ID);

        ArgumentCaptor<HealthDataAttachment> createAttachmentArgCaptor = ArgumentCaptor.forClass(
                HealthDataAttachment.class);
        when(mockHealthDataService.createOrUpdateAttachment(createAttachmentArgCaptor.capture())).thenReturn(
                ATTACHMENT_ID_BAR, ATTACHMENT_ID_FOO);

        when(mockHealthDataService.getRecordById(TEST_RECORD_ID)).thenReturn(intermediateRecord);

        when(mockHealthDataService.getAttachmentBuilder()).thenAnswer(
                invocation -> new DynamoHealthDataAttachment.Builder());

        when(mockHealthDataService.getRecordBuilder()).thenAnswer(invocation -> new DynamoHealthDataRecord.Builder());

        // mock S3 helper
        S3Helper mockS3Helper = mock(S3Helper.class);

        // set up handler
        UploadArtifactsHandler handler = new UploadArtifactsHandler();
        handler.setHealthDataService(mockHealthDataService);
        handler.setS3Helper(mockS3Helper);

        // set up context
        // To make sure tests are consistent, use a TreeMap for the attachment map, so that the keys are returned in
        // alphabetical order.
        Map<String, byte[]> attachmentMap = new TreeMap<>();
        attachmentMap.put("bar.txt", "This is bar".getBytes(Charsets.UTF_8));
        attachmentMap.put("foo.txt", "This is foo".getBytes(Charsets.UTF_8));

        // Most important thing in the record builder is the data map. It's the same as the one we expect back in the
        // intermediate record
        HealthDataRecordBuilder recordBuilder = createValidRecordBuilder(dataJson);

        // only need upload ID from upload
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);

        UploadValidationContext context = new UploadValidationContext();
        context.setAttachmentsByFieldName(attachmentMap);
        context.setHealthDataRecordBuilder(recordBuilder);
        context.setUpload(upload);

        // execute
        handler.handle(context);

        // validate result - create record
        List<HealthDataRecord> createRecordArgList = createRecordArgCaptor.getAllValues();
        assertEquals(2, createRecordArgList.size());

        // First record is the intermediate record. This will have data, but no record ID, since record ID is assigned
        // by this call.
        HealthDataRecord createIntermediateRecordArg = createRecordArgList.get(0);
        assertEquals("dummy-healthcode", createIntermediateRecordArg.getHealthCode());
        assertNull(createIntermediateRecordArg.getId());
        assertEquals(ARBITRARY_TIMESTAMP, createIntermediateRecordArg.getCreatedOn().longValue());
        assertEquals("dummy-schema", createIntermediateRecordArg.getSchemaId());
        assertEquals(1, createIntermediateRecordArg.getSchemaRevision());
        assertEquals("dummy-study", createIntermediateRecordArg.getStudyId());
        assertEquals("2015-11-18", createIntermediateRecordArg.getUploadDate().toString(ISODateTimeFormat.date()));
        assertEquals(TEST_UPLOAD_ID, createIntermediateRecordArg.getUploadId());
        assertEquals("dummy-external-ID", createIntermediateRecordArg.getUserExternalId());
        assertEquals(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS, createIntermediateRecordArg.getUserSharingScope());
        assertEquals(TestConstants.USER_DATA_GROUPS, createIntermediateRecordArg.getUserDataGroups());
        assertEquals(42, createIntermediateRecordArg.getVersion().longValue());

        assertTrue(createIntermediateRecordArg.getMetadata().isObject());
        assertEquals(0, createIntermediateRecordArg.getMetadata().size());

        JsonNode createIntermediateRecordDataJson = createIntermediateRecordArg.getData();
        assertEquals(2, createIntermediateRecordDataJson.size());
        assertEquals("This is a string", createIntermediateRecordDataJson.get("json.json.string").textValue());
        assertEquals(42, createIntermediateRecordDataJson.get("json.json.int").intValue());

        // Second record is the final record. This has data (with attachments) and record ID;
        HealthDataRecord createFinalRecordArg = createRecordArgList.get(1);
        assertEquals("dummy-healthcode", createFinalRecordArg.getHealthCode());
        assertEquals(TEST_RECORD_ID, createFinalRecordArg.getId());
        assertEquals(ARBITRARY_TIMESTAMP, createFinalRecordArg.getCreatedOn().longValue());
        assertEquals("dummy-schema", createFinalRecordArg.getSchemaId());
        assertEquals(1, createFinalRecordArg.getSchemaRevision());
        assertEquals("dummy-study", createFinalRecordArg.getStudyId());
        assertEquals("2015-11-18", createFinalRecordArg.getUploadDate().toString(ISODateTimeFormat.date()));
        assertEquals(TEST_UPLOAD_ID, createFinalRecordArg.getUploadId());
        assertEquals("dummy-external-ID", createFinalRecordArg.getUserExternalId());
        assertEquals(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS, createFinalRecordArg.getUserSharingScope());
        assertEquals(TestConstants.USER_DATA_GROUPS, createFinalRecordArg.getUserDataGroups());
        assertEquals(42, createFinalRecordArg.getVersion().longValue());

        assertTrue(createFinalRecordArg.getMetadata().isObject());
        assertEquals(0, createFinalRecordArg.getMetadata().size());

        JsonNode createFinalRecordDataJson = createFinalRecordArg.getData();
        assertEquals(4, createFinalRecordDataJson.size());
        assertEquals("This is a string", createFinalRecordDataJson.get("json.json.string").textValue());
        assertEquals(42, createFinalRecordDataJson.get("json.json.int").intValue());
        assertEquals(ATTACHMENT_ID_BAR, createFinalRecordDataJson.get("bar.txt").textValue());
        assertEquals(ATTACHMENT_ID_FOO, createFinalRecordDataJson.get("foo.txt").textValue());

        // validate - create attachment. The order doesn't matter, since the call to create
        // attachment only includes the record ID, which is the same for all attachments in a given record.
        List<HealthDataAttachment> createAttachmentArgList = createAttachmentArgCaptor.getAllValues();
        assertEquals(2, createAttachmentArgList.size());
        for (HealthDataAttachment oneAttachment : createAttachmentArgList) {
            assertEquals(TEST_RECORD_ID, oneAttachment.getRecordId());
        }

        // validate - S3 uploads
        verify(mockS3Helper).writeBytesToS3(TestConstants.ATTACHMENT_BUCKET, ATTACHMENT_ID_BAR, BYTES_BAR);
        verify(mockS3Helper).writeBytesToS3(TestConstants.ATTACHMENT_BUCKET, ATTACHMENT_ID_FOO, BYTES_FOO);

        // validate record ID in the context
        assertEquals(TEST_RECORD_ID, context.getRecordId());

        // validate no messages on the context
        assertTrue(context.getMessageList().isEmpty());
    }

    // creates a record builder that has all the valid values filled in, with the data JsonNode specified
    private static HealthDataRecordBuilder createValidRecordBuilder(JsonNode dataNode) {
        // none of these values matter (except data, which is specified), so just fill in whatever
        // All that matters is that the values are written to DDB.
        return new DynamoHealthDataRecord.Builder().withCreatedOn(ARBITRARY_TIMESTAMP).withData(dataNode)
                .withHealthCode("dummy-healthcode").withMetadata(BridgeObjectMapper.get().createObjectNode())
                .withSchemaId("dummy-schema").withSchemaRevision(1).withStudyId("dummy-study")
                .withUploadDate(LocalDate.parse("2015-11-18")).withUploadId(TEST_UPLOAD_ID)
                .withUserExternalId("dummy-external-ID")
                .withUserSharingScope(ParticipantOption.SharingScope.SPONSORS_AND_PARTNERS)
                .withUserDataGroups(TestConstants.USER_DATA_GROUPS)
                .withVersion(42L);
    }
}
