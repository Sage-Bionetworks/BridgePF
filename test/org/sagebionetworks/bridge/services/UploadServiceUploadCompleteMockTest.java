package org.sagebionetworks.bridge.services;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.models.upload.UploadCompletionClient.APP;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

@SuppressWarnings("unchecked")
public class UploadServiceUploadCompleteMockTest {
    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_UPLOAD_ID = "test-upload";

    private AmazonS3 mockS3Client;
    private UploadDao mockUploadDao;
    private UploadValidationService mockUploadValidationService;
    private UploadService svc;

    @Before
    public void setup() {
        // mock config
        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.getProperty(UploadService.CONFIG_KEY_UPLOAD_BUCKET)).thenReturn(TEST_BUCKET);

        // set up mocks - The actual behavior will vary with each test.
        mockS3Client = mock(AmazonS3.class);
        mockUploadDao = mock(UploadDao.class);
        mockUploadValidationService = mock(UploadValidationService.class);

        // Set up service
        svc = new UploadService();
        svc.setConfig(mockConfig);
        svc.setS3Client(mockS3Client);
        svc.setUploadDao(mockUploadDao);
        svc.setUploadValidationService(mockUploadValidationService);
    }

    @Test
    public void validationInProgress() {
        // set up input
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.VALIDATION_IN_PROGRESS);

        // execute
        svc.uploadComplete(TEST_STUDY, APP, upload);

        // Verify upload DAO and validation aren't called. Can skip S3 because we don't want to over-specify our tests.
        verifyZeroInteractions(mockUploadDao, mockUploadValidationService);
    }

    @Test
    public void notFoundInS3() {
        // set up input
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.REQUESTED);

        // mock S3
        when(mockS3Client.getObjectMetadata(TEST_BUCKET, TEST_UPLOAD_ID)).thenThrow(AmazonClientException.class);

        // execute
        try {
            svc.uploadComplete(TEST_STUDY, APP, upload);
            fail("expected exception");
        } catch (NotFoundException ex) {
            // expected exception
        }

        // Verify upload DAO and validation aren't called.
        verifyZeroInteractions(mockUploadDao, mockUploadValidationService);
    }

    @Test
    public void uploadSuceeded() {
        // set up input
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.SUCCEEDED);

        // execute
        svc.uploadComplete(TEST_STUDY, APP, upload);

        // Verify S3, upload DAO and validation aren't called.
        verifyZeroInteractions(mockUploadDao, mockUploadValidationService, mockS3Client);
    }

    @Test
    public void concurrentModification() {
        // set up input
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.REQUESTED);

        // mock S3
        ObjectMetadata mockObjMetadata = mock(ObjectMetadata.class);
        when(mockObjMetadata.getSSEAlgorithm()).thenReturn(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        when(mockS3Client.getObjectMetadata(TEST_BUCKET, TEST_UPLOAD_ID)).thenReturn(mockObjMetadata);

        // mock uploadDao.uploadComplete()
        doThrow(ConcurrentModificationException.class).when(mockUploadDao).uploadComplete(APP, upload);

        // execute
        svc.uploadComplete(TestConstants.TEST_STUDY, APP, upload);

        // Verify upload DAO and validation.
        verify(mockUploadValidationService, never()).validateUpload(any(StudyIdentifier.class), any(Upload.class));
    }

    @Test
    public void normalCase() {
        // set up input
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.REQUESTED);

        // mock S3
        ObjectMetadata mockObjMetadata = mock(ObjectMetadata.class);
        when(mockObjMetadata.getSSEAlgorithm()).thenReturn(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        when(mockS3Client.getObjectMetadata(TEST_BUCKET, TEST_UPLOAD_ID)).thenReturn(mockObjMetadata);

        // execute
        svc.uploadComplete(TestConstants.TEST_STUDY, APP, upload);

        // Verify upload DAO and validation.
        verify(mockUploadDao).uploadComplete(APP, upload);
        verify(mockUploadValidationService).validateUpload(TestConstants.TEST_STUDY, upload);
    }
}
