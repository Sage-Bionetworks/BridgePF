package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;

public class UploadValidationContextTest {
    @Test
    public void shallowCopy() {
        // dummy objects to test against
        Study study = new DynamoStudy();
        User user = new User();
        Upload upload = new DynamoUpload2();
        byte[] data = "test-data".getBytes(Charsets.UTF_8);
        byte[] decryptedData = "test-decrypted-data".getBytes(Charsets.UTF_8);
        Map<String, byte[]> unzippedDataMap = ImmutableMap.of("nonJsonFile.txt", "test text".getBytes(Charsets.UTF_8));
        Map<String, JsonNode> jsonDataMap = ImmutableMap.<String, JsonNode>of("json.json",
                BridgeObjectMapper.get().createObjectNode());
        HealthDataRecordBuilder recordBuilder = new DynamoHealthDataRecord.Builder();
        Map<String, byte[]> attachmentMap = ImmutableMap.of("test-field", "test attachment".getBytes(Charsets.UTF_8));

        // create original
        UploadValidationContext original = new UploadValidationContext();
        original.setStudy(study);
        original.setUser(user);
        original.setUpload(upload);
        original.setSuccess(false);
        original.addMessage("common message");
        original.setData(data);
        original.setDecryptedData(decryptedData);
        original.setUnzippedDataMap(unzippedDataMap);
        original.setJsonDataMap(jsonDataMap);
        original.setHealthDataRecordBuilder(recordBuilder);
        original.setAttachmentsByFieldName(attachmentMap);

        // copy and validate
        UploadValidationContext copy = original.shallowCopy();
        assertSame(study, copy.getStudy());
        assertSame(user, copy.getUser());
        assertSame(upload, copy.getUpload());
        assertFalse(copy.getSuccess());
        assertSame(data, copy.getData());
        assertSame(decryptedData, copy.getDecryptedData());
        assertSame(unzippedDataMap, copy.getUnzippedDataMap());
        assertSame(jsonDataMap, copy.getJsonDataMap());
        assertSame(recordBuilder, copy.getHealthDataRecordBuilder());
        assertSame(attachmentMap, copy.getAttachmentsByFieldName());

        assertEquals(1, copy.getMessageList().size());
        assertEquals("common message", copy.getMessageList().get(0));

        // modify original and validate copy unchanged
        original.setData("new-data".getBytes(Charsets.UTF_8));
        original.addMessage("original message");

        assertSame(data, copy.getData());
        assertEquals(1, copy.getMessageList().size());
        assertEquals("common message", copy.getMessageList().get(0));

        // modify copy and validate original unchanged
        copy.setDecryptedData("new-decrypted-data".getBytes(Charsets.UTF_8));
        copy.addMessage("copy message");

        assertSame(decryptedData, original.getDecryptedData());
        assertEquals(2, original.getMessageList().size());
        assertEquals("common message", original.getMessageList().get(0));
        assertEquals("original message", original.getMessageList().get(1));
    }
}
