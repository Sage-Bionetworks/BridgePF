package org.sagebionetworks.bridge.services;

import static com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UploadServiceTest {
    
    private static final String BUCKET = BridgeConfigFactory.getConfig().getProperty("upload.bucket");

    @Resource
    private UploadService uploadService;

    @Resource
    private UploadDao uploadDao;

    @Resource
    private AmazonS3 s3Client;

    @Resource
    private TestUserAdminHelper helper;
    
    private TestUser testUser;

    private List<String> objectsToRemove;

    private final String text = "This text is being uploaded as an object.";

    @Before
    public void before() {
        assertNotNull(uploadService);
        assertNotNull(s3Client);
        objectsToRemove = new ArrayList<String>();
        testUser = helper.getBuilder(UploadServiceTest.class).build();
    }

    @After
    public void after() {
        try {
            for (String obj : objectsToRemove) {
                s3Client.deleteObject(BUCKET, obj);
            }
        } catch (AmazonClientException e) {
            e.printStackTrace();
        }
        helper.deleteUser(testUser);
    }

    @Test
    public void test() throws Exception {
        UploadRequest uploadRequest = createUploadRequest();
        UploadSession uploadSession = uploadService.createUpload(TestConstants.TEST_STUDY,
                testUser.getStudyParticipant(), uploadRequest);
        final String uploadId = uploadSession.getId();
        objectsToRemove.add(uploadId);
        int reponseCode = upload(uploadSession.getUrl(), uploadRequest);
        assertEquals(200, reponseCode);

        Upload upload = uploadService.getUpload(testUser.getStudyParticipant(), uploadId);
        uploadService.uploadComplete(TestConstants.TEST_STUDY, upload);
        long expiration = DateTime.now(DateTimeZone.UTC).plusMinutes(1).getMillis();
        assertTrue(expiration > uploadSession.getExpires());
        ObjectMetadata obj = s3Client.getObjectMetadata(BUCKET, uploadId);
        String sse = obj.getSSEAlgorithm();
        assertTrue(AES_256_SERVER_SIDE_ENCRYPTION.equals(sse));
    }

    private UploadRequest createUploadRequest() throws Exception {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("name", this.getClass().getSimpleName());
        node.put("contentType", "text/plain");
        node.put("contentLength", text.getBytes().length);
        node.put("contentMd5", Base64.encodeBase64String(DigestUtils.md5(text)));
        return UploadRequest.fromJson(node);
    }

    private int upload(String url, UploadRequest uploadRequest) throws IOException, URISyntaxException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPut httpPut = new HttpPut(url);
        httpPut.setEntity(new StringEntity(text));
        // The following two hears are used for signing. Must add them.
        httpPut.addHeader("Content-MD5", uploadRequest.getContentMd5());
        httpPut.addHeader("Content-Type", uploadRequest.getContentType());
        CloseableHttpResponse response = httpclient.execute(httpPut);
        try {
            return response.getStatusLine().getStatusCode();
        } finally {
            response.close();
        }
    }
}
