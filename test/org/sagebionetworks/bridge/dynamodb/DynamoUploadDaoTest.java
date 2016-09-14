package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

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
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoUploadDaoTest {
    private static final DateTime MOCK_NOW = DateTime.parse("2016-04-12T15:00:00-0700");
    private static final String ORIGINAL_UPLOAD_ID = "original-upload-id";
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

    private Set<String> uploadIds;

    @Before
    public void setup() {
        // clear state, since JUnit doesn't always do so
        uploadIds = Sets.newHashSet();
    }

    @After
    public void cleanup() {
        DateTimeUtils.setCurrentMillisSystem();

        if (!uploadIds.isEmpty()) {
            for (String uploadId : uploadIds) {
                DynamoUpload2 upload = (DynamoUpload2) dao.getUpload(uploadId);
                mapper.delete(upload);
            }
        }
    }

    private UploadRequest createRequest() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW.getMillis());

        // Create upload request. For some reason, this can only be created through JSON.
        String uploadRequestJsonText = "{\n" +
                "   \"contentLength\":" + UPLOAD_CONTENT_LENGTH + ",\n" +
                "   \"contentMd5\":\"" + UPLOAD_CONTENT_MD5 + "\",\n" +
                "   \"contentType\":\"" + UPLOAD_CONTENT_TYPE + "\",\n" +
                "   \"name\":\"" + UPLOAD_NAME + "\"\n" +
                "}";
        JsonNode uploadRequestJsonNode = BridgeObjectMapper.get().readTree(uploadRequestJsonText);
        return UploadRequest.fromJson(uploadRequestJsonNode);
    }
    
    @Test
    public void test() throws Exception {
        UploadRequest uploadRequest = createRequest();

        // create upload
        DynamoUpload2 upload = (DynamoUpload2) dao.createUpload(uploadRequest, TEST_STUDY, TEST_HEALTH_CODE, null);
        assertUpload(upload);
        assertEquals(UploadStatus.REQUESTED, upload.getStatus());
        assertEquals(TEST_STUDY_IDENTIFIER, upload.getStudyId());
        assertNotNull(upload.getUploadId());
        assertNull(upload.getDuplicateUploadId());
        uploadIds.add(upload.getUploadId());

        // get upload back from dao
        DynamoUpload2 fetchedUpload = (DynamoUpload2) dao.getUpload(upload.getUploadId());
        assertUpload(fetchedUpload);

        // Fetch it again. We'll need a second copy to test concurrent modification exceptions later.
        DynamoUpload2 fetchedUpload2 = (DynamoUpload2) dao.getUpload(upload.getUploadId());

        // upload complete
        dao.uploadComplete(UploadCompletionClient.S3_WORKER, fetchedUpload);

        // second call to upload complete throws ConcurrentModificationException
        try {
            dao.uploadComplete(UploadCompletionClient.APP, fetchedUpload2);
            fail("expected exception");
        } catch (ConcurrentModificationException ex) {
            // expected exception
        }

        // fetch completed upload
        DynamoUpload2 completedUpload = (DynamoUpload2) dao.getUpload(upload.getUploadId());
        assertUpload(completedUpload);
        assertEquals(UploadStatus.VALIDATION_IN_PROGRESS, completedUpload.getStatus());
        assertEquals(MOCK_NOW.toLocalDate(), completedUpload.getUploadDate());
    }

    @Test
    public void testDuplicate() throws Exception {
        // Most of the stuff in this code path has already been tested in test(). So this simplified test tests the new
        // parameters for dedupe logic.

        UploadRequest uploadRequest = createRequest();

        // create upload - We still care about study ID and requestedOn for reporting, as well as dupe attributes.
        DynamoUpload2 upload = (DynamoUpload2) dao.createUpload(uploadRequest, TEST_STUDY, TEST_HEALTH_CODE,
                ORIGINAL_UPLOAD_ID);
        uploadIds.add(upload.getUploadId());
        assertEquals(ORIGINAL_UPLOAD_ID, upload.getDuplicateUploadId());
        assertEquals(UploadStatus.DUPLICATE, upload.getStatus());
        assertEquals(TEST_STUDY_IDENTIFIER, upload.getStudyId());
        assertEquals(MOCK_NOW.getMillis(), upload.getRequestedOn());

        // We don't call Upload Complete in this scenario.
    }

    @Test
    public void canRetrieveUploadRecords() throws Exception {
        int numUploads = 2;
        Map<String, String> healthcodeToUploadId = new HashMap<>();
        for (int i = 0; i < numUploads; i++) {
            String healthcode = "code" + i;
            UploadRequest uploadRequest = createRequest();
            Upload upload = dao.createUpload(uploadRequest, TEST_STUDY, healthcode, null);
            String uploadId = upload.getUploadId();

            uploadIds.add(uploadId);
            healthcodeToUploadId.put(healthcode, uploadId);
        }

        // GSIs are eventually consistent. Try sleeping here.
        Thread.sleep(2000);

        DateTime now = DateTime.now();

        List<? extends Upload> uploads = dao.getUploads("code0", now.minusMinutes(1), now);
        assertEquals(1, uploads.size());
        assertEquals(healthcodeToUploadId.get("code0"), uploads.get(0).getUploadId());
        
        // This is a range outside of the just created records... should not return anything.
        uploads = dao.getUploads("code0", now.minusMinutes(3), now.minusMinutes(2));
        assertEquals(0, uploads.size());
        
        // Now verify you can get this through the study-based call
        uploads = dao.getStudyUploads(TEST_STUDY, now.minusMinutes(1), now);
        assertEquals(numUploads, uploads.size());

        // DDB can return uploads in any order. This is true, regardless of mocking DateTime.now().
        Set<String> foundHealthCodeSet = new HashSet<>();
        Set<String> foundUploadIdSet = new HashSet<>();
        for (Upload oneUpload : uploads) {
            String foundHealthCode = oneUpload.getHealthCode();
            String foundUploadId = oneUpload.getUploadId();
            assertEquals(healthcodeToUploadId.get(foundHealthCode), foundUploadId);

            foundHealthCodeSet.add(foundHealthCode);
            foundUploadIdSet.add(foundUploadId);
        }
        assertEquals(healthcodeToUploadId.keySet(), foundHealthCodeSet);
        assertEquals(numUploads, foundUploadIdSet.size());
        assertTrue(foundUploadIdSet.containsAll(healthcodeToUploadId.values()));

        uploads = dao.getStudyUploads(TEST_STUDY, now.minusMinutes(3), now.minusMinutes(2));
        assertEquals(0, uploads.size());
    }
    
    @Test
    public void uploadRecordsEmpty() throws Exception {
        List<? extends Upload> uploads = dao.getUploads("nonexistentCode", DateTime.now().minusMinutes(1),
                DateTime.now());
        
        assertTrue(uploads.isEmpty());
    }

    private static void assertUpload(DynamoUpload2 upload) {
        assertEquals(UPLOAD_CONTENT_LENGTH, upload.getContentLength());
        assertEquals(UPLOAD_CONTENT_MD5, upload.getContentMd5());
        assertEquals(UPLOAD_CONTENT_TYPE, upload.getContentType());
        assertEquals(UPLOAD_NAME, upload.getFilename());
        assertEquals(TEST_HEALTH_CODE, upload.getHealthCode());
    }
}
