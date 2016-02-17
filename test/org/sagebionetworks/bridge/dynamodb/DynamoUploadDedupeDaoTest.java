package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoUploadDedupeDaoTest {
    private static final String TEST_HEALTHCODE = "test-healthcode";
    private static final String TEST_ORIGINAL_UPLOAD_ID = "original-upload";
    private static final String TEST_UPLOAD_MD5 = "test-md5";
    private static final DateTime TEST_UPLOAD_REQUESTED_ON = DateTime.parse("2016-02-15T10:26:45-0800");

    @Autowired
    @SuppressWarnings("unused")
    private DynamoUploadDedupeDao dao;

    @Resource(name = "uploadDedupeDdbMapper")
    @SuppressWarnings("unused")
    private DynamoDBMapper mapper;

    @Before
    public void setup() {
        dao.registerUpload(TEST_HEALTHCODE, TEST_UPLOAD_MD5, TEST_UPLOAD_REQUESTED_ON, TEST_ORIGINAL_UPLOAD_ID);
    }

    @After
    public void cleanup() {
        DynamoUploadDedupe keyObj = new DynamoUploadDedupe();
        keyObj.setHealthCode(TEST_HEALTHCODE);
        keyObj.setUploadMd5(TEST_UPLOAD_MD5);
        keyObj.setUploadRequestedOn(TEST_UPLOAD_REQUESTED_ON.getMillis());
        mapper.delete(keyObj);
    }

    @Test
    public void isDupe() {
        // After 1 day, it's still a dupe.
        String originalUploadId = dao.getDuplicate(TEST_HEALTHCODE, TEST_UPLOAD_MD5,
                TEST_UPLOAD_REQUESTED_ON.plusDays(1));
        assertEquals(TEST_ORIGINAL_UPLOAD_ID, originalUploadId);
    }

    @Test
    public void differentHealthCode() {
        String originalUploadId = dao.getDuplicate("different-healthcode", TEST_UPLOAD_MD5,
                TEST_UPLOAD_REQUESTED_ON.plusDays(1));
        assertNull(originalUploadId);
    }

    @Test
    public void differentMd5() {
        String originalUploadId = dao.getDuplicate(TEST_HEALTHCODE, "different-md5",
                TEST_UPLOAD_REQUESTED_ON.plusDays(1));
        assertNull(originalUploadId);
    }

    @Test
    public void outsideWindow() {
        String originalUploadId = dao.getDuplicate(TEST_HEALTHCODE, TEST_UPLOAD_MD5,
                TEST_UPLOAD_REQUESTED_ON.plusDays(8));
        assertNull(originalUploadId);
    }
}
