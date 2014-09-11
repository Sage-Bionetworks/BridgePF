package org.sagebionetworks.bridge.services;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UploadServiceTest {

    @Resource
    private UploadService uploadService;

    @Test
    public void test() throws Exception {
        URL url = uploadService.createUpload();
        System.out.println(url.toString());
        upload(url);
    }

    private void upload(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-MD5", "cc9982c0879594647c3fd05eb9534097");
        conn.setRequestProperty("Content-Type", "text/plain");
        OutputStreamWriter out = new OutputStreamWriter(
                conn.getOutputStream());
        out.write("This text uploaded as object.");
        out.close();
        System.out.println(conn.getResponseMessage());
        System.out.println(conn.getResponseCode());
    }
}
