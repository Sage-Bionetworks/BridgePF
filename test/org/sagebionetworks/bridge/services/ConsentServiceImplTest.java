package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ConsentServiceImplTest {
    
    private static final Long UNIX_TIMESTAMP = DateTime.now().getMillis();
    private static final Withdrawal WITHDRAWAL = new Withdrawal("For reasons.");

    @Resource
    private JedisOps jedisOps;
    
    @Resource
    private ConsentServiceImpl consentService;
    
    @Resource
    private StudyConsentService studyConsentService;
    
    @Resource
    private AccountDao accountDao;

    @Resource
    private UserConsentDao userConsentDao;

    @Resource
    private StudyService studyService;

    @Resource
    private ParticipantOptionsService optionsService;

    @Resource
    private TestUserAdminHelper helper;

    private StudyConsentForm defaultConsentDocument;
    
    @Value("classpath:study-defaults/consent-body.xhtml")
    public void setDefaultConsentDocument(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultConsentDocument = new StudyConsentForm(IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8));
    }
    
    private Study study;
    
    private TestUser testUser;

    @Before
    public void before() {
        study = TestUtils.getValidStudy(ConsentServiceImplTest.class);
        studyService.createStudy(study);
        
        testUser = helper.createUser(ConsentServiceImplTest.class, study, false);
    }

    @After
    public void after() {
        helper.deleteUser(testUser);
        studyService.deleteStudy(study.getIdentifier());
    }

    @Test
    public void userHasNotGivenConsent() {
        assertFalse(consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        try {
            consentService.getConsentSignature(testUser.getStudy(), testUser.getUser());
            fail("expected exception");
        } catch (EntityNotFoundException e) {
        }
    }
    
    @Test
    public void canConsent() {
        // Consent and verify.
        ConsentSignature researchConsent = new ConsentSignature.Builder().withName("John Smith")
                .withBirthdate("1990-11-11").build();
        consentService.consentToResearch(testUser.getStudy(), testUser.getUser(), researchConsent, 
                SharingScope.ALL_QUALIFIED_RESEARCHERS, false);
        
        assertTrue(consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        ConsentSignature returnedSig = consentService.getConsentSignature(testUser.getStudy(), testUser.getUser());
        assertEquals("John Smith", returnedSig.getName());
        assertEquals("1990-11-11", returnedSig.getBirthdate());
        assertNull(returnedSig.getImageData());
        assertNull(returnedSig.getImageMimeType());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, optionsService.getEnum(testUser.getUser().getHealthCode(), SHARING_SCOPE, SharingScope.class));

        // Withdraw consent and verify.
        consentService.withdrawConsent(testUser.getStudy(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);
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
        ConsentSignature signature = new ConsentSignature.Builder().withName("Eggplant McTester")
                .withBirthdate("1970-01-01").withImageData(TestConstants.DUMMY_IMAGE_DATA)
                .withImageMimeType("image/fake").build();
        
        long signedOn = signature.getSignedOn();
        
        consentService.consentToResearch(testUser.getStudy(), testUser.getUser(), signature, SharingScope.NO_SHARING, false);
        assertTrue(consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));

        ConsentSignature returnedSig = consentService.getConsentSignature(testUser.getStudy(), testUser.getUser());
        
        assertEquals("Eggplant McTester", returnedSig.getName());
        assertEquals("1970-01-01", returnedSig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, returnedSig.getImageData());
        assertEquals("image/fake", returnedSig.getImageMimeType());
        assertEquals(signedOn, returnedSig.getSignedOn());

        // Withdraw consent and verify.
        consentService.withdrawConsent(testUser.getStudy(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);
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
        LocalDate today18YearsAgo = now.minusYears(18);
        LocalDate yesterday18YearsAgo = today18YearsAgo.minusDays(1);
        LocalDate tomorrow18YearsAgo = today18YearsAgo.plusDays(1);
        SharingScope sharingScope = SharingScope.NO_SHARING;

        // This will work
        ConsentSignature sig = new ConsentSignature.Builder().withName("Test User")
                .withBirthdate(DateUtils.getCalendarDateString(today18YearsAgo)).build();
        consentService.consentToResearch(study, testUser.getUser(), sig, sharingScope, false);
        
        consentService.withdrawConsent(study, testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);

        // Also okay
        sig = new ConsentSignature.Builder().withName("Test User")
                .withBirthdate(DateUtils.getCalendarDateString(yesterday18YearsAgo)).build();
        consentService.consentToResearch(study, testUser.getUser(), sig, sharingScope, false);
        consentService.withdrawConsent(study, testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);

        // But this is not, one day to go
        try {
            sig = new ConsentSignature.Builder().withName("Test User")
                    .withBirthdate(DateUtils.getCalendarDateString(tomorrow18YearsAgo)).build();
            consentService.consentToResearch(study, testUser.getUser(), sig, sharingScope, false);
            fail("This should throw an exception");
        } catch (InvalidEntityException e) {
            assertTrue(e.getMessage().contains("years of age or older"));
        }
    }
    
    @Test
    public void enforcesStudyEnrollmentLimit() {
        String key = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey("test");
        try {
            jedisOps.del(key);
            
            Study study = TestUtils.getValidStudy(ConsentServiceImplTest.class);
            study.setMaxNumOfParticipants(2);

            // Set the cache so we avoid going to DynamoDB. We're testing the caching layer
            // in the service test, we'll test the DAO in the DAO test.
            jedisOps.del(key);

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
            jedisOps.del(key);
        }
    }

    @Test
    public void checkConsentUpToDate() {
        ConsentSignature consent = new ConsentSignature.Builder().withName("John Smith")
                .withBirthdate("1990-11-11").build();

        consentService.consentToResearch(testUser.getStudy(), testUser.getUser(), consent,
                SharingScope.SPONSORS_AND_PARTNERS, false);

        assertTrue("Should be consented",
                consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        assertTrue("Should have signed most recent consent.",
                consentService.hasUserSignedActiveConsent(testUser.getStudy(), testUser.getUser()));

        // Create new study consent, but do not activate it. User is consented and has still signed most recent consent.
        StudyConsent newStudyConsent = studyConsentService.addConsent(testUser.getStudyIdentifier(), defaultConsentDocument).getStudyConsent();

        assertTrue("Should be consented.",
                consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        assertTrue("Should have signed most recent consent, even though new consent is added.",
                consentService.hasUserSignedActiveConsent(testUser.getStudy(), testUser.getUser()));

        // Activate new study consent. User is consented and but has not signed most recent consent.
        newStudyConsent = studyConsentService.publishConsent(study, newStudyConsent.getCreatedOn()).getStudyConsent();

        assertTrue("Should still be consented.",
                consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        assertFalse("New consent activated. Should no longer have signed most recent consent. ",
                consentService.hasUserSignedActiveConsent(testUser.getStudy(), testUser.getUser()));

        // To consent again, first need to withdraw. User is consented and has now signed most recent consent.
        consentService.withdrawConsent(testUser.getStudy(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);
        consentService.consentToResearch(testUser.getStudy(), testUser.getUser(),
            new ConsentSignature.Builder().withConsentSignature(consent).withSignedOn(DateTime.now().getMillis()).build(),
            SharingScope.SPONSORS_AND_PARTNERS, false);

        assertTrue("Should still be consented.",
                consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        assertTrue("Should again have signed most recent consent.",
                consentService.hasUserSignedActiveConsent(testUser.getStudy(), testUser.getUser()));
    }
    
    @Test
    public void withdrawConsent() {
        long originalSignedOn = DateTime.now().getMillis();
        ConsentSignature consent = new ConsentSignature.Builder().withName("John Smith")
                .withImageData("data").withImageMimeType("image/png").withBirthdate("1990-11-11")
                .withSignedOn(originalSignedOn).build();

        consentService.consentToResearch(testUser.getStudy(), testUser.getUser(), consent,
                SharingScope.SPONSORS_AND_PARTNERS, false);

        // Consent exists, user is consented
        assertTrue(consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        assertTrue(testUser.getUser().doesConsent());
        assertNotNull(consentService.getConsentSignature(testUser.getStudy(), testUser.getUser()));
        
        // Now withdraw consent
        consentService.withdrawConsent(testUser.getStudy(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);
        
        // Now user is not consented
        assertFalse(consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        assertFalse(testUser.getUser().doesConsent());
        try {
            consentService.getConsentSignature(testUser.getStudy(), testUser.getUser());            
        } catch(EntityNotFoundException e) {
            assertEquals("ConsentSignature not found.", e.getMessage());
        }
        
        // Verify that the consent signature history serializes/deserializes correctly and stores the 
        // (now) historical signature
        Account account = accountDao.getAccount(testUser.getStudy(), testUser.getEmail());
        assertEquals(1, account.getConsentSignatures().size());
        ConsentSignature historicalSignature = account.getConsentSignatures().get(0);
        
        assertEquals(consent.getName(), historicalSignature.getName());
        assertEquals(consent.getBirthdate(), historicalSignature.getBirthdate());
        assertEquals(consent.getSignedOn(), historicalSignature.getSignedOn());
        
        assertNull(account.getActiveConsentSignature());
        
        long newSignedOn = DateTime.now().getMillis();
        consent = new ConsentSignature.Builder().withConsentSignature(consent).withSignedOn(newSignedOn).build();
        consentService.consentToResearch(testUser.getStudy(), testUser.getUser(), consent,
                SharingScope.SPONSORS_AND_PARTNERS, false);
        
        // Finally, verify there is a history for this user.
        List<UserConsentHistory> history = consentService.getUserConsentHistory(testUser.getStudy(), testUser.getUser());
        assertEquals(2, history.size());
        assertNotNull(history.get(0).getWithdrewOn());
        assertNull(history.get(1).getWithdrewOn());
        
        StudyConsent studyConsent = studyConsentService.getActiveConsent(study).getStudyConsent();
        UserConsent userConsent = userConsentDao.getUserConsent(testUser.getUser().getHealthCode(), study, originalSignedOn);
        
        UserConsentHistory historyItem = history.get(0);
        assertEquals(testUser.getUser().getHealthCode(), historyItem.getHealthCode());
        assertEquals(study.getIdentifier(), historyItem.getStudyIdentifier());
        assertEquals(studyConsent.getCreatedOn(), historyItem.getConsentCreatedOn());
        assertEquals(consent.getName(), historyItem.getName());
        assertEquals(consent.getBirthdate(), historyItem.getBirthdate());
        assertEquals(consent.getImageData(), historyItem.getImageData());
        assertEquals(consent.getImageMimeType(), historyItem.getImageMimeType());
        assertEquals(originalSignedOn, historyItem.getSignedOn());
        assertEquals(userConsent.getWithdrewOn(), historyItem.getWithdrewOn());
        assertEquals(true, historyItem.isHasSignedActiveConsent());
    }
}