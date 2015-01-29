package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.upload.Upload;

public class UploadServiceMockTest {
    @Test(expected = BadRequestException.class)
    public void getNullUploadId() {
        new UploadService().getUpload(null);
    }

    @Test(expected = BadRequestException.class)
    public void getEmptyUploadId() {
        new UploadService().getUpload("");
    }

    @Test
    public void getUpload() {
        // This is a simple call through to the DAO. Test the data flow.

        // mock upload dao
        UploadDao mockDao = mock(UploadDao.class);
        Upload mockUpload = new DynamoUpload2();
        when(mockDao.getUpload("test-upload-id")).thenReturn(mockUpload);

        // create test service with mock
        UploadService svc = new UploadService();
        svc.setUploadDao(mockDao);

        // execute and validate
        Upload retVal = svc.getUpload("test-upload-id");
        assertSame(mockUpload, retVal);
    }
}
