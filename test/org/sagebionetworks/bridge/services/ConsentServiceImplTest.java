package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
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
    
    private TestUser testUser;

    @Before
    public void before() {
        testUser = helper.createUser(ConsentServiceImplTest.class);
        studyConsent = studyConsentDao.addConsent(testUser.getStudy().getIdentifier(), "/path/to", testUser.getStudy()
                .getMinAgeOfConsent());
        studyConsentDao.setActive(studyConsent, true);

        // TestUserAdminHelper creates a user with consent. Withdraw consent to make sure we're
        // working with a clean slate.
        consentService.withdrawConsent(testUser.getUser(), testUser.getStudy());

        // Ensure that user gives no consent.
        assertFalse(consentService.hasUserConsentedToResearch(testUser.getUser(), testUser.getStudy()));
        EntityNotFoundException thrownEx = null;
        try {
            consentService.getConsentSignature(testUser.getUser(), testUser.getStudy());
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            thrownEx = ex;
        }
        assertNotNull(thrownEx);
    }

    @After
    public void after() {
        if (consentService.hasUserConsentedToResearch(testUser.getUser(), testUser.getStudy())) {
            consentService.withdrawConsent(testUser.getUser(), testUser.getStudy());
        }
        studyConsentDao.setActive(studyConsent, false);
        studyConsentDao.deleteConsent(testUser.getStudy().getIdentifier(), studyConsent.getCreatedOn());
        helper.deleteUser(testUser);
    }

    @Test
    public void test() {
        // Consent and verify.
        ConsentSignature researchConsent = ConsentSignature.create("John Smith", "2011-11-11", null, null);
        consentService.consentToResearch(testUser.getUser(), researchConsent, testUser.getStudy(), false);
        assertTrue(consentService.hasUserConsentedToResearch(testUser.getUser(), testUser.getStudy()));
        ConsentSignature returnedSig = consentService.getConsentSignature(testUser.getUser(), testUser.getStudy());
        assertEquals("John Smith", returnedSig.getName());
        assertEquals("2011-11-11", returnedSig.getBirthdate());
        assertNull(returnedSig.getImageData());
        assertNull(returnedSig.getImageMimeType());

        // Withdraw consent and verify.
        consentService.withdrawConsent(testUser.getUser(), testUser.getStudy());
        assertFalse(consentService.hasUserConsentedToResearch(testUser.getUser(), testUser.getStudy()));
        EntityNotFoundException thrownEx = null;
        try {
            consentService.getConsentSignature(testUser.getUser(), testUser.getStudy());
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            thrownEx = ex;
        }
        assertNotNull(thrownEx);
    }

    @Test
    public void withSignatureImage() {
        // Consent and verify.
        ConsentSignature researchConsent = ConsentSignature.create("Eggplant McTester", "1970-01-01",
                TestConstants.DUMMY_IMAGE_DATA, "image/fake");
        consentService.consentToResearch(testUser.getUser(), researchConsent, testUser.getStudy(), false);
        assertTrue(consentService.hasUserConsentedToResearch(testUser.getUser(), testUser.getStudy()));
        ConsentSignature returnedSig = consentService.getConsentSignature(testUser.getUser(), testUser.getStudy());
        assertEquals("Eggplant McTester", returnedSig.getName());
        assertEquals("1970-01-01", returnedSig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, returnedSig.getImageData());
        assertEquals("image/fake", returnedSig.getImageMimeType());

        // Withdraw consent and verify.
        consentService.withdrawConsent(testUser.getUser(), testUser.getStudy());
        assertFalse(consentService.hasUserConsentedToResearch(testUser.getUser(), testUser.getStudy()));
        EntityNotFoundException thrownEx = null;
        try {
            consentService.getConsentSignature(testUser.getUser(), testUser.getStudy());
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            thrownEx = ex;
        }
        assertNotNull(thrownEx);
    }
}
