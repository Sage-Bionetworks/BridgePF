package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UploadServiceTest {

    @Resource
    private UploadService uploadService;

    @Resource
    private AmazonS3 s3Client;

    private List<String> objectsToRemove;

    @Before
    public void before() {
        assertNotNull(uploadService);
        assertNotNull(s3Client);
        objectsToRemove = new ArrayList<String>();
        String bucket = BridgeConfigFactory.getConfig().getProperty("upload.bucket.pd");
        // Clean objects older than an hour
        ObjectListing objList = s3Client.listObjects(bucket);
        for (S3ObjectSummary obj: objList.getObjectSummaries()) {
            Date date = obj.getLastModified();
            Date now = DateTime.now(DateTimeZone.UTC).minusHours(1).toDate();
            if (now.after(date)) {
                s3Client.deleteObject(bucket, obj.getKey());
            }
        }
    }

    @After
    public void after() {
        String bucket = BridgeConfigFactory.getConfig().getProperty("upload.bucket.pd");
        for (String obj : objectsToRemove) {
            s3Client.deleteObject(bucket, obj);
        }
    }

    @Test
    public void test() throws Exception {
        URL url = uploadService.createUpload();
        int reponseCode = upload(url);
        assertEquals(200, reponseCode);
        String uploadId = url.getPath();
        uploadId = uploadId.substring(1); // Remove the leading '/'
        uploadService.uploadComplete(uploadId);
        objectsToRemove.add(uploadId);
    }

    private int upload(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        OutputStreamWriter out = new OutputStreamWriter(
                conn.getOutputStream());
        out.write("This text uploaded as object.");
        out.close();
        int responseCode = conn.getResponseCode();
        conn.disconnect();
        return responseCode;
    }
}
