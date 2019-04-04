package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;

import com.amazonaws.services.s3.model.ObjectMetadata;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.s3.S3Helper;

public class UploadRawZipHandlerTest {
    private static final String UPLOAD_ID = "my-upload";

    @Test
    public void test() {
        // Set up mocks and handler.
        S3Helper mockS3Helper = mock(S3Helper.class);
        UploadRawZipHandler handler = new UploadRawZipHandler();
        handler.setS3Helper(mockS3Helper);
        
        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);;

        // Set up context. We only read the upload ID, decrypted data file (mock), and the record.
        UploadValidationContext context = new UploadValidationContext();

        Upload upload = Upload.create();
        upload.setUploadId(UPLOAD_ID);
        context.setUpload(upload);

        File mockDecryptedFile = mock(File.class);
        context.setDecryptedDataFile(mockDecryptedFile);

        HealthDataRecord record = HealthDataRecord.create();
        context.setHealthDataRecord(record);

        // Execute and validate.
        handler.handle(context);

        String expectedRawDataAttachmentId = UPLOAD_ID + UploadRawZipHandler.RAW_ATTACHMENT_SUFFIX;
        verify(mockS3Helper).writeFileToS3(eq(UploadRawZipHandler.ATTACHMENT_BUCKET), eq(expectedRawDataAttachmentId),
                eq(mockDecryptedFile), metadataCaptor.capture());

        assertEquals(expectedRawDataAttachmentId, record.getRawDataAttachmentId());
        assertEquals(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION, metadataCaptor.getValue().getSSEAlgorithm());
    }
}
