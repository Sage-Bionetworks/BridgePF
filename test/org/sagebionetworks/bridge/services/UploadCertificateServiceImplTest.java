package org.sagebionetworks.bridge.services;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UploadCertificateServiceImplTest {

    @Resource
    private UploadCertificateService uploadCertificateService;

    @Test
    public void test() {
        String studyIdentifier = getClass().getSimpleName();
        uploadCertificateService.createCmsKeyPair(studyIdentifier);
    }
}
