package org.sagebionetworks.bridge.models.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord.ExporterStatus;

/**
 * An API view of uploads that combines information from our internal upload health data record tables.
 * We serialize this in the API but do not read it through the API.  
 */
public class UploadViewTest {
    
    private static final DateTime REQUESTED_ON = DateTime.parse("2016-07-25T16:25:32.211Z");
    private static final DateTime COMPLETED_ON = DateTime.parse("2016-07-25T16:25:32.277Z");
    
    @Test
    public void canSerialize() throws Exception {
        HealthDataRecord record = HealthDataRecord.create();
        record.setAppVersion("appVersion");
        record.setCreatedOn(COMPLETED_ON.getMillis());
        record.setCreatedOnTimeZone("+03:00");
        record.setData(TestUtils.getClientData());
        record.setHealthCode("healthCode");
        record.setId("id");
        record.setMetadata(TestUtils.getClientData());
        record.setPhoneInfo("phoneInfo");
        record.setSchemaId("schema-id");
        record.setSchemaRevision(5);
        record.setStudyId("studyId");
        record.setUploadDate(LocalDate.parse("2016-10-10"));
        record.setUploadId("upload-id");
        record.setUploadedOn(REQUESTED_ON.getMillis());
        record.setUserMetadata(TestUtils.getClientData());
        record.setUserSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        record.setUserExternalId("external-id");
        record.setUserDataGroups(TestConstants.USER_DATA_GROUPS);
        record.setValidationErrors("some errors");
        record.setVersion(1L);
        record.setSynapseExporterStatus(ExporterStatus.SUCCEEDED);
        
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setContentLength(1000L);
        upload.setStatus(UploadStatus.SUCCEEDED);
        upload.setRequestedOn(REQUESTED_ON.getMillis());
        upload.setCompletedOn(COMPLETED_ON.getMillis());
        upload.setCompletedBy(UploadCompletionClient.APP);
        // These should be hidden by the @JsonIgnore property
        upload.setContentMd5("some-content");
        upload.setHealthCode("health-code");
        
        UploadView view = new UploadView.Builder().withUpload(upload)
                .withHealthDataRecord(record)
                .withSchemaId("schema-name")
                .withSchemaRevision(10)
                .withHealthRecordExporterStatus(HealthDataRecord.ExporterStatus.SUCCEEDED).build();

        JsonNode node = BridgeObjectMapper.get().valueToTree(view);
        
        assertEquals(1000, node.get("contentLength").intValue());
        assertEquals("succeeded", node.get("status").textValue());
        assertEquals("2016-07-25T16:25:32.211Z", node.get("requestedOn").textValue());
        assertEquals("2016-07-25T16:25:32.277Z", node.get("completedOn").textValue());
        assertEquals("app", node.get("completedBy").textValue());
        assertEquals("schema-name", node.get("schemaId").textValue());
        assertEquals(10, node.get("schemaRevision").intValue());
        assertEquals("Upload", node.get("type").textValue());
        assertEquals("succeeded", node.get("healthRecordExporterStatus").textValue());
        
        JsonNode recordNode = node.get("healthData");
        assertEquals("appVersion", recordNode.get("appVersion").textValue());
        assertEquals(COMPLETED_ON.toString(), recordNode.get("createdOn").textValue());
        assertEquals("+03:00", recordNode.get("createdOnTimeZone").textValue());
        assertEquals("id", recordNode.get("id").textValue());
        assertEquals("phoneInfo", recordNode.get("phoneInfo").textValue());
        assertEquals("schema-id", recordNode.get("schemaId").textValue());
        assertEquals(5, recordNode.get("schemaRevision").intValue());
        assertEquals("studyId", recordNode.get("studyId").textValue());
        assertEquals("2016-10-10", recordNode.get("uploadDate").textValue());
        assertEquals("upload-id", recordNode.get("uploadId").textValue());
        assertEquals(REQUESTED_ON.toString(), recordNode.get("uploadedOn").textValue());
        assertEquals("all_qualified_researchers", recordNode.get("userSharingScope").textValue());
        assertEquals("external-id", recordNode.get("userExternalId").textValue());
        assertTrue(TestConstants.USER_DATA_GROUPS.contains(recordNode.get("userDataGroups").get(0).textValue()));
        assertTrue(TestConstants.USER_DATA_GROUPS.contains(recordNode.get("userDataGroups").get(1).textValue()));
        assertEquals("some errors", recordNode.get("validationErrors").textValue());
        assertEquals(1L, recordNode.get("version").longValue());
        assertEquals("succeeded", recordNode.get("synapseExporterStatus").textValue());
        assertEquals("healthCode", recordNode.get("healthCode").textValue());
        
        assertTrue(recordNode.get("data").isObject());
        assertTrue(recordNode.get("metadata").isObject());
        assertTrue(recordNode.get("userMetadata").isObject());
        
        // With recent changes to expose to admin, these should be present in JSON
        assertEquals("some-content", node.get("contentMd5").textValue());
        assertEquals("health-code", node.get("healthCode").textValue());
    }

}
