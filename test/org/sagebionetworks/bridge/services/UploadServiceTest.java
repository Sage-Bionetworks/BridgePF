package org.sagebionetworks.bridge.services;

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
    public void test() {
        URL url = uploadService.createUpload();
        System.out.println(url.toString());
    }
}
