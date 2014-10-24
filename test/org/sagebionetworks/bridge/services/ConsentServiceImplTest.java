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
import org.sagebionetworks.bridge.models.UserSession;
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
    
    private UserSession session;

    @Before
    public void before() {
        session = helper.createUser(getClass().getSimpleName());
        studyConsent = studyConsentDao.addConsent(helper.getTestStudy().getKey(), "/path/to", helper.getTestStudy()
                .getMinAge());
        studyConsentDao.setActive(studyConsent, true);
    }

    @After
    public void after() {
        helper.deleteUser(session, getClass().getSimpleName());
        studyConsentDao.setActive(studyConsent, false);
        studyConsentDao.deleteConsent(helper.getTestStudy().getKey(), studyConsent.getCreatedOn());
    }

    @Test
    public void test() {
        ConsentSignature researchConsent = new ConsentSignature("John Smith", "2011-11-11");
        boolean sendEmail = false;

        // Withdrawing and consenting again should return to original state.
        consentService.withdrawConsent(session.getUser(), helper.getTestStudy());
        consentService.consentToResearch(session.getUser(), researchConsent, helper.getTestStudy(), sendEmail);
        boolean hasConsented = consentService.hasUserConsentedToResearch(session.getUser(), helper.getTestStudy());
        assertTrue(hasConsented);

        // Suspend sharing should make isSharingData return false.
        consentService.suspendDataSharing(session.getUser(), helper.getTestStudy());
        boolean isSharing = consentService.isSharingData(session.getUser(), helper.getTestStudy());
        assertFalse(isSharing);

        // Resume sharing should make isSharingData return true.
        consentService.resumeDataSharing(session.getUser(), helper.getTestStudy());
        isSharing = consentService.isSharingData(session.getUser(), helper.getTestStudy());

    }
}
