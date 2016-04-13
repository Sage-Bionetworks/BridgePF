package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoUploadDaoTest {
    private static final DateTime MOCK_NOW = DateTime.parse("2016-04-12T15:00:00-0700");
    private static final String TEST_HEALTH_CODE = "test-health-code";
    private static final int UPLOAD_CONTENT_LENGTH = 1213;
    private static final String UPLOAD_CONTENT_MD5 = "fFROLXJeXfzQvXYhJRKNfg==";
    private static final String UPLOAD_CONTENT_TYPE = "application/zip";
    private static final String UPLOAD_NAME = "test-upload";

    @Autowired
    private DynamoUploadDao dao;

    @Resource(name = "uploadDdbMapper")
    @SuppressWarnings("unused")
    private DynamoDBMapper mapper;

    private String uploadId;

    @Before
    public void setup() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW.getMillis());

        // clear state, since JUnit doesn't always do so
        uploadId = null;
    }

    @After
    public void cleanup() {
        DateTimeUtils.setCurrentMillisSystem();

        if (uploadId != null) {
            DynamoUpload2 upload = (DynamoUpload2) dao.getUpload(uploadId);
            mapper.delete(upload);
        }
    }

    @Test
    public void test() throws Exception {
        // Create upload request. For some reason, this can only be created through JSON.
        String uploadRequestJsonText = "{\n" +
                "   \"contentLength\":" + UPLOAD_CONTENT_LENGTH + ",\n" +
                "   \"contentMd5\":\"" + UPLOAD_CONTENT_MD5 + "\",\n" +
                "   \"contentType\":\"" + UPLOAD_CONTENT_TYPE + "\",\n" +
                "   \"name\":\"" + UPLOAD_NAME + "\"\n" +
                "}";
        JsonNode uploadRequestJsonNode = BridgeObjectMapper.get().readTree(uploadRequestJsonText);
        UploadRequest uploadRequest = UploadRequest.fromJson(uploadRequestJsonNode);

        // create upload
        DynamoUpload2 upload = (DynamoUpload2) dao.createUpload(uploadRequest, TEST_HEALTH_CODE);
        assertUpload(upload);
        assertEquals(UploadStatus.REQUESTED, upload.getStatus());
        assertNotNull(upload.getUploadId());
        uploadId = upload.getUploadId();

        // get upload back from dao
        DynamoUpload2 fetchedUpload = (DynamoUpload2) dao.getUpload(uploadId);
        assertUpload(fetchedUpload);

        // Fetch it again. We'll need a second copy to test concurrent modification exceptions later.
        DynamoUpload2 fetchedUpload2 = (DynamoUpload2) dao.getUpload(uploadId);

        // upload complete
        dao.uploadComplete(fetchedUpload);

        // second call to upload complete throws ConcurrentModificationException
        try {
            dao.uploadComplete(fetchedUpload2);
            fail("expected exception");
        } catch (ConcurrentModificationException ex) {
            // expected exception
        }

        // fetch completed upload
        DynamoUpload2 completedUpload = (DynamoUpload2) dao.getUpload(uploadId);
        assertUpload(completedUpload);
        assertEquals(UploadStatus.VALIDATION_IN_PROGRESS, completedUpload.getStatus());
        assertEquals(MOCK_NOW.toLocalDate(), completedUpload.getUploadDate());
    }

    private static void assertUpload(DynamoUpload2 upload) {
        assertEquals(UPLOAD_CONTENT_LENGTH, upload.getContentLength());
        assertEquals(UPLOAD_CONTENT_MD5, upload.getContentMd5());
        assertEquals(UPLOAD_CONTENT_TYPE, upload.getContentType());
        assertEquals(UPLOAD_NAME, upload.getFilename());
        assertEquals(TEST_HEALTH_CODE, upload.getHealthCode());
    }
}
