package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.upload.Upload;

public class UploadServiceMockTest {
    @Test(expected = BadRequestException.class)
    public void getNullUploadId() {
        new UploadService().getUpload(makeUser("nullUploadId"), null);
    }

    @Test(expected = BadRequestException.class)
    public void getEmptyUploadId() {
        new UploadService().getUpload(makeUser("emptyUploadId"), "");
    }

    @Test
    public void getUpload() {
        // This is a simple call through to the DAO. Test the data flow.

        // mock upload dao
        UploadDao mockDao = mock(UploadDao.class);
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setHealthCode("getUpload");
        when(mockDao.getUpload("test-upload-id")).thenReturn(mockUpload);

        // create test service with mock
        UploadService svc = new UploadService();
        svc.setUploadDao(mockDao);

        // execute and validate
        Upload retVal = svc.getUpload(makeUser("getUpload"), "test-upload-id");
        assertSame(mockUpload, retVal);
    }

    @Test(expected = UnauthorizedException.class)
    public void mismatchedHealthCode() {
        // mock upload dao
        UploadDao mockDao = mock(UploadDao.class);
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setHealthCode("wrong-health-code");
        when(mockDao.getUpload("mismatched-health-codes")).thenReturn(mockUpload);

        // create test service with mock
        UploadService svc = new UploadService();
        svc.setUploadDao(mockDao);

        // execute
        svc.getUpload(makeUser("right-health-code"), "mismatched-health-codes");
    }

    // Helper method for creating users. UploadService only cares about healthCode, so that's the only thing we set.
    private static User makeUser(String healthCode) {
        User user = new User();
        user.setHealthCode(healthCode);
        return user;
    }
}
