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
    public void canConsent() {
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
    public void canConsentWithSignatureImage() {
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
    
    @Test
    public void cannotConsentIfTooYoung() {
        Study study = new DynamoStudy();
        study.setIdentifier("test");
        study.setName("Test Study");
        study.setMinAgeOfConsent(18);
        
        try {
            ConsentSignature sig = ConsentSignature.create("Test User", "2008-08-08", null, null);
            consentService.consentToResearch(testUser.getUser(), sig, study, false);
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("years of age or older"));
        }
    }
    
    @Test
    public void enforcesStudyEnrollmentLimit() {
        Study study = new DynamoStudy();
        study.setIdentifier("test");
        study.setName("Test Study");
        study.setMaxNumOfParticipants(2);
        
        // Set the cache so we avoid going to DynamoDB. We're testing the caching layer 
        // in the service test, we'll test the DAO in the DAO test.
        JedisStringOps stringOps = new JedisStringOps();
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        stringOps.delete(key).execute();
        
        boolean limit = consentService.isStudyAtEnrollmentLimit(study);
        assertFalse("No limit reached", limit);
        
        consentService.incrementStudyEnrollment(study);
        consentService.incrementStudyEnrollment(study);
        limit = consentService.isStudyAtEnrollmentLimit(study);
        assertTrue("Limit reached", limit);
        try {
            consentService.incrementStudyEnrollment(study);
            fail("Should have thrown an exception");
        } catch(StudyLimitExceededException e) {
            assertEquals("This is a 473 error", 473, e.getStatusCode());
        }
        stringOps.delete(key).execute();
    }
}
