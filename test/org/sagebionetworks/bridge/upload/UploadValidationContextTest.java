package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;

public class UploadValidationContextTest {
    private static final String HEALTH_CODE = "health-code";
    private static final String UPLOAD_ID = "upload-id";

    @Test
    public void uploadId() {
        // null upload returns null upload ID
        UploadValidationContext context = new UploadValidationContext();
        assertNull(context.getUploadId());

        // non-null upload returns the ID of that upload
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID);
        context.setUpload(upload);
        assertEquals(UPLOAD_ID, context.getUploadId());
    }

    @Test
    public void shallowCopy() {
        // dummy objects to test against
        Study study = TestUtils.getValidStudy(UploadValidationContextTest.class);
        Upload upload = new DynamoUpload2();
        File tempDir = mock(File.class);
        File dataFile = mock(File.class);
        File decryptedDataFile = mock(File.class);
        Map<String, File> unzippedDataFileMap = ImmutableMap.<String, File>builder().put("foo", mock(File.class))
                .put("bar", mock(File.class)).put("baz", mock(File.class)).build();
        JsonNode infoJsonNode = BridgeObjectMapper.get().createObjectNode();
        HealthDataRecord record = HealthDataRecord.create();

        // create original
        UploadValidationContext original = new UploadValidationContext();
        original.setHealthCode(HEALTH_CODE);
        original.setStudy(study);
        original.setUpload(upload);
        original.setSuccess(false);
        original.addMessage("common message");
        original.setTempDir(tempDir);
        original.setDataFile(dataFile);
        original.setDecryptedDataFile(decryptedDataFile);
        original.setUnzippedDataFileMap(unzippedDataFileMap);
        original.setInfoJsonNode(infoJsonNode);
        original.setHealthDataRecord(record);
        original.setRecordId("test-record");

        // copy and validate
        UploadValidationContext copy = original.shallowCopy();
        assertEquals(HEALTH_CODE, copy.getHealthCode());
        assertSame(study, copy.getStudy());
        assertSame(upload, copy.getUpload());
        assertFalse(copy.getSuccess());
        assertSame(tempDir, copy.getTempDir());
        assertSame(dataFile, copy.getDataFile());
        assertSame(decryptedDataFile, copy.getDecryptedDataFile());
        assertEquals(unzippedDataFileMap, copy.getUnzippedDataFileMap());
        assertSame(infoJsonNode, copy.getInfoJsonNode());
        assertSame(record, copy.getHealthDataRecord());
        assertEquals("test-record", copy.getRecordId());

        assertEquals(1, copy.getMessageList().size());
        assertEquals("common message", copy.getMessageList().get(0));

        // modify original and validate copy unchanged
        original.setHealthCode("new-health-code");
        original.addMessage("original message");

        assertEquals(HEALTH_CODE, copy.getHealthCode());
        assertEquals(1, copy.getMessageList().size());
        assertEquals("common message", copy.getMessageList().get(0));

        // modify copy and validate original unchanged
        copy.setRecordId("new-record-id");
        copy.addMessage("copy message");

        assertEquals("test-record", original.getRecordId());
        assertEquals(2, original.getMessageList().size());
        assertEquals("common message", original.getMessageList().get(0));
        assertEquals("original message", original.getMessageList().get(1));
    }
}
