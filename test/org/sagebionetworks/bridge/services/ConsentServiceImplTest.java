package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.annotation.Resource;

import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.redis.JedisStringOps;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ConsentServiceImplTest {

    private StudyConsent studyConsent;

    @Resource
    private JedisStringOps stringOps;
    
    @Resource
    private ConsentService consentService;

    @Resource
    private StudyConsentDao studyConsentDao;

    @Resource
    private UserConsentDao userConsentDao;

    @Resource
    private StudyService studyService;
    
    @Resource
    private TestUserAdminHelper helper;

    private Study study;
    
    private TestUser testUser;

    @Before
    public void before() {
        study = studyService.getStudy("api");
        testUser = helper.createUser(ConsentServiceImplTest.class, true, false, null);
        
        studyConsent = studyConsentDao.addConsent(study.getStudyIdentifier(), "/path/to", study.getMinAgeOfConsent());
        studyConsentDao.setActive(studyConsent, true);

        // Ensure that user gives no consent.
        assertFalse(consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        try {
            consentService.getConsentSignature(testUser.getStudy(), testUser.getUser());
            fail("expected exception");
        } catch (EntityNotFoundException e) {
        }
    }

    @After
    public void after() {
        if (consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser())) {
            consentService.withdrawConsent(testUser.getStudy(), testUser.getUser());
        }
        studyConsentDao.setActive(studyConsent, false);
        studyConsentDao.deleteConsent(testUser.getStudyIdentifier(), studyConsent.getCreatedOn());
        helper.deleteUser(testUser);
    }

    @Test
    public void canConsent() {
        // Consent and verify.
        ConsentSignature researchConsent = ConsentSignature.create("John Smith", "1990-11-11", null, null);
        consentService.consentToResearch(testUser.getStudy(), testUser.getUser(), researchConsent, false);
        assertTrue(consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        ConsentSignature returnedSig = consentService.getConsentSignature(testUser.getStudy(), testUser.getUser());
        assertEquals("John Smith", returnedSig.getName());
        assertEquals("1990-11-11", returnedSig.getBirthdate());
        assertNull(returnedSig.getImageData());
        assertNull(returnedSig.getImageMimeType());

        // Withdraw consent and verify.
        consentService.withdrawConsent(testUser.getStudy(), testUser.getUser());
        assertFalse(consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        try {
            consentService.getConsentSignature(testUser.getStudy(), testUser.getUser());
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
        }
    }
    
    @Test
    public void canConsentWithSignatureImage() {
        // Consent and verify.
        ConsentSignature signature = ConsentSignature.create("Eggplant McTester", "1970-01-01",
                TestConstants.DUMMY_IMAGE_DATA, "image/fake");
        consentService.consentToResearch(testUser.getStudy(), testUser.getUser(), signature, false);
        assertTrue(consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        
        ConsentSignature returnedSig = consentService.getConsentSignature(testUser.getStudy(), testUser.getUser());
        
        assertEquals("Eggplant McTester", returnedSig.getName());
        assertEquals("1970-01-01", returnedSig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, returnedSig.getImageData());
        assertEquals("image/fake", returnedSig.getImageMimeType());

        // Withdraw consent and verify.
        consentService.withdrawConsent(testUser.getStudy(), testUser.getUser());
        assertFalse(consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        try {
            consentService.getConsentSignature(testUser.getStudy(), testUser.getUser());
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
        }
    }

    @Test
    public void cannotConsentIfTooYoung() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthOfYear();
        int day = now.getDayOfMonth();

        // The API study is set for 17 years... just go with that as it exists, it's easiest
        String today18YearsAgo = String.format("%s-%2d-%2d", year - 18, month, day).replaceAll(" ", "0");
        String yesterday18YearsAgo = String.format("%s-%2d-%2d", year - 18, month, day - 1).replaceAll(" ", "0");
        String tomorrow18YearsAgo = String.format("%s-%2d-%2d", year - 18, month, day + 1).replaceAll(" ", "0");

        // This will work
        ConsentSignature sig = ConsentSignature.create("Test User", today18YearsAgo, null, null);
        consentService.consentToResearch(study, testUser.getUser(), sig, false);
        consentService.withdrawConsent(study, testUser.getUser());

        // Also okay
        sig = ConsentSignature.create("Test User", yesterday18YearsAgo, null, null);
        consentService.consentToResearch(study, testUser.getUser(), sig, false);
        consentService.withdrawConsent(study, testUser.getUser());

        // But this is not, one day to go
        try {
            sig = ConsentSignature.create("Test User", tomorrow18YearsAgo, null, null);
            consentService.consentToResearch(study, testUser.getUser(), sig, false);
        } catch (InvalidEntityException e) {
            consentService.withdrawConsent(study, testUser.getUser());
            assertTrue(e.getMessage().contains("years of age or older"));
        }
    }
    
    @Test
    public void enforcesStudyEnrollmentLimit() {
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey("test");
        try {
            stringOps.delete(key);
            
            Study study = new DynamoStudy();
            study.setIdentifier("test");
            study.setName("Test Study");
            study.setMaxNumOfParticipants(2);

            // Set the cache so we avoid going to DynamoDB. We're testing the caching layer
            // in the service test, we'll test the DAO in the DAO test.
            stringOps.delete(key);

            boolean limit = consentService.isStudyAtEnrollmentLimit(study);
            assertFalse("No limit reached", limit);

            consentService.incrementStudyEnrollment(study);
            consentService.incrementStudyEnrollment(study);
            limit = consentService.isStudyAtEnrollmentLimit(study);
            assertTrue("Limit reached", limit);
            try {
                consentService.incrementStudyEnrollment(study);
                fail("Should have thrown an exception");
            } catch (StudyLimitExceededException e) {
                assertEquals("This is a 473 error", 473, e.getStatusCode());
            }
        } finally {
            stringOps.delete(key);
        }
    }

    @Test
    public void checkConsentUpToDate() {
        StudyConsent newStudyConsent = null;
        try {
            ConsentSignature researchConsent = ConsentSignature.create("John Smith", "1990-11-11", null, null);
            consentService.consentToResearch(testUser.getStudy(), testUser.getUser(), researchConsent, false);

            assertTrue("Should be consented",
                    consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
            assertTrue("Should have signed most recent consent.",
                    consentService.hasUserSignedMostRecentConsent(testUser.getStudy(), testUser.getUser()));

            // Create new study consent, but do not activate it. User is consented and has still signed most recent consent.
            newStudyConsent = studyConsentDao.addConsent(testUser.getStudyIdentifier(), "/path/to2",
                    testUser.getStudy().getMinAgeOfConsent());

            assertTrue("Should be consented.",
                    consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
            assertTrue("Should have signed most recent consent, even though new consent is added.",
                    consentService.hasUserSignedMostRecentConsent(testUser.getStudy(), testUser.getUser()));

            // Activate new study consent. User is consented and but has not signed most recent consent.
            newStudyConsent = studyConsentDao.setActive(newStudyConsent, true);

            assertTrue("Should still be consented.",
                    consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
            assertFalse("New consent activated. Should no longer have signed most recent consent. ",
                    consentService.hasUserSignedMostRecentConsent(testUser.getStudy(), testUser.getUser()));

            // To consent again, first need to withdraw. User is consented and has now signed most recent consent.
            consentService.withdrawConsent(testUser.getStudy(), testUser.getUser());
            consentService.consentToResearch(testUser.getStudy(), testUser.getUser(), researchConsent, false);

            assertTrue("Should still be consented.",
                    consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
            assertTrue("Should again have signed most recent consent.",
                    consentService.hasUserSignedMostRecentConsent(testUser.getStudy(), testUser.getUser()));
        } finally {
            studyConsentDao.deleteConsent(testUser.getStudyIdentifier(), newStudyConsent.getCreatedOn());
        }
    }
    
}
