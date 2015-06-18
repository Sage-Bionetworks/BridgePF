package org.sagebionetworks.bridge.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.email.ConsentEmailProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SendEmailIntegTest {

    private static final String IMG = "iVBORw0KGgoAAAANSUhEUgAAAFAAAAAtCAIAAAC2z3vlAAAAA3NCSVQICAjb" +
            "4U/gAAABhElEQVRoge1Z0Q7DIAgsy/7/l9nDkq5pEUUOMGvvaeuqcB6gMmLm7U54VTuQjdsRfotPiej7QQn4/Z" +
            "3um0uBro62mJyei1ifdp+wFYtzxuewc72iIeewE0RC4GDntw75JSY8pK82rAiKka8/ZoVFGqKLEzonpMMoYd11" +
            "ZnZyTst8WNHyJG0C2909ZNESddZFhlA1rTW4SrdiW0T3zRaT00BTsQjZlkags43b1fAHj66vRKSwZebuDJ6tNF" +
            "thnWqCA3mEsVRNxeKIoZD2r325sDsyGgDrsN0Kq3TVLbKvcIRnoTmio+A+XNshyG7ilfdDzjnsPNzm1KerlfHJ" +
            "kxQuF3YHknBL3mi2pvmfRvwsnPIeh+tDnFUGoLB++xmcQfk6+NMgvIQzr7WiLasJc0hX9dlRds/NESAf09rrWy" +
            "uw/kddHhJOy3MmQralhGPGtImCnpZ/lGdBwYTXOUK2gCTsZDs43GkFULSAql5bc/CQ0f4uXT8+JxD7z/WCuN1t" +
            "6SH877gd4Q/1IdhFWU79+gAAAABJRU5ErkJggg==";

    @Resource
    private StudyService studyService;

    @Resource
    private StudyConsentService studyConsentService;
    
    @Resource
    private SendMailViaAmazonService sendEmailService;

    private String consentBodyTemplate;
    
    @Value("classpath:study-defaults/consent-page.xhtml")
    final void setConsentBodyTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.consentBodyTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    
    @Test
    public void test() {
        final ConsentSignature signature = ConsentSignature.create("Eggplant McTester", "1970-05-01", IMG, "image/png");
        final User user = new User();
        user.setEmail("bridge-testing@sagebase.org");
        final Study study = studyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER);
        
        sendEmailService.sendEmail(new ConsentEmailProvider(study, user, signature, 
            SharingScope.SPONSORS_AND_PARTNERS, studyConsentService, consentBodyTemplate));
    }
    
}