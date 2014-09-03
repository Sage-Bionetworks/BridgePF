package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.stormpath.sdk.client.Client;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ConsentServiceImplTest {

    private StudyConsent studyConsent;

    @Resource
    private Client stormpathClient;

    @Resource
    private ConsentService consentService;

    @Resource
    private StudyConsentDao studyConsentDao;

    @Resource
    private UserConsentDao userConsentDao;

    @Resource
    private TestUserAdminHelper helper;

    @Before
    public void before() {
        helper.createOneUser();
        studyConsent = studyConsentDao
                .addConsent(helper.getStudy().getKey(), "/path/to", helper.getStudy().getMinAge());
        studyConsentDao.setActive(studyConsent, true);
    }

    @After
    public void after() {
        helper.deleteOneUser();
        studyConsentDao.setActive(studyConsent, false);
        studyConsentDao.deleteConsent(helper.getStudy().getKey(), studyConsent.getCreatedOn());
    }

    @Test
    public void test() {
        ConsentSignature researchConsent = new ConsentSignature("John Smith", "2011-11-11");
        boolean sendEmail = false;

        // Withdrawing and consenting again should return to original state.
        consentService.withdrawConsent(helper.getUser(), helper.getStudy());
        consentService.consentToResearch(helper.getUser(), researchConsent, helper.getStudy(), sendEmail);
        boolean hasConsented = consentService.hasUserConsentedToResearch(helper.getUser(), helper.getStudy());
        assertTrue(hasConsented);

        // Suspend sharing should make isSharingData return false.
        consentService.suspendDataSharing(helper.getUser(), helper.getStudy());
        boolean isSharing = consentService.isSharingData(helper.getUser(), helper.getStudy());
        assertFalse(isSharing);

        // Resume sharing should make isSharingData return true.
        consentService.resumeDataSharing(helper.getUser(), helper.getStudy());
        isSharing = consentService.isSharingData(helper.getUser(), helper.getStudy());

    }
}
