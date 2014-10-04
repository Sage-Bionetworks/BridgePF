package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.models.UploadRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoUploadDaoTest {

    @Resource
    private DynamoUploadDao uploadDao;

    @Before
    public void before() {
        DynamoInitializer.init(DynamoUpload.class);
        DynamoTestUtil.clearTable(DynamoUpload.class);
    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoUpload.class);
    }

    @Test
    public void test() {
        UploadRequest uploadRequest = createUploadRequest();
        String healthCode = "fakeHealthCode";
        String uploadId = uploadDao.createUpload(uploadRequest, healthCode);
        assertNotNull(uploadId);
        assertFalse(uploadDao.isComplete(uploadId));
        String objectId = uploadDao.getObjectId(uploadId);
        assertNotNull(objectId);
        assertFalse(uploadId.equals(objectId));
        uploadDao.uploadComplete(uploadId);
        assertTrue(uploadDao.isComplete(uploadId));
    }

    private UploadRequest createUploadRequest() {
        final String text = "test upload dao";
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("name", this.getClass().getSimpleName());
        node.put("contentType", "text/plain");
        node.put("contentLength", text.getBytes().length);
        node.put("contentMd5", Base64.encodeBase64String(DigestUtils.md5(text)));
        return UploadRequest.fromJson(node);
    }
}
