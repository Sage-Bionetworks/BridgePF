package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.json.DateUtils;
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

    private static final long UNIX_TIMESTAMP = DateUtils.getCurrentMillisFromEpoch();
    
    @Resource
    private JedisOps jedisOps;
    
    @Resource
    private ConsentServiceImpl consentService;
    
    @Resource
    private StudyConsentService studyConsentService;

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
        
        testUser = helper.createUser(ConsentServiceImplTest.class, study, false, null);
    }

    @After
    public void after() {
        if (consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser())) {
            consentService.withdrawConsent(testUser.getStudy(), testUser.getUser());
        }
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
                .withBirthdate("1990-11-11").withSignedOn(UNIX_TIMESTAMP).build();
        consentService.consentToResearch(testUser.getStudy(), testUser.getUser(), researchConsent, 
                SharingScope.ALL_QUALIFIED_RESEARCHERS, false);
        
        assertTrue(consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        ConsentSignature returnedSig = consentService.getConsentSignature(testUser.getStudy(), testUser.getUser());
        assertEquals("John Smith", returnedSig.getName());
        assertEquals("1990-11-11", returnedSig.getBirthdate());
        assertNull(returnedSig.getImageData());
        assertNull(returnedSig.getImageMimeType());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, optionsService.getSharingScope(testUser.getUser().getHealthCode()));

        // Withdraw consent and verify.
        consentService.withdrawConsent(testUser.getStudy(), testUser.getUser());
        assertFalse(consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        try {
            consentService.getConsentSignature(testUser.getStudy(), testUser.getUser());
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
        }
        assertEquals(0, optionsService.getAllParticipantOptions(testUser.getUser().getHealthCode()).size());
    }
    
    @Test
    public void canConsentWithSignatureImage() {
        // Consent and verify.
        ConsentSignature signature = new ConsentSignature.Builder().withName("Eggplant McTester")
                .withBirthdate("1970-01-01").withImageData(TestConstants.DUMMY_IMAGE_DATA)
                .withImageMimeType("image/fake").withSignedOn(UNIX_TIMESTAMP).build();
        consentService.consentToResearch(testUser.getStudy(), testUser.getUser(), signature, SharingScope.NO_SHARING, false);
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
        LocalDate today18YearsAgo = now.minusYears(18);
        LocalDate yesterday18YearsAgo = today18YearsAgo.minusDays(1);
        LocalDate tomorrow18YearsAgo = today18YearsAgo.plusDays(1);
        SharingScope sharingScope = SharingScope.NO_SHARING;

        // This will work
        ConsentSignature sig = new ConsentSignature.Builder().withName("Test User")
                .withBirthdate(DateUtils.getCalendarDateString(today18YearsAgo)).withSignedOn(UNIX_TIMESTAMP).build();
        consentService.consentToResearch(study, testUser.getUser(), sig, sharingScope, false);
        
        consentService.withdrawConsent(study, testUser.getUser());

        // Also okay
        sig = new ConsentSignature.Builder().withName("Test User")
                .withBirthdate(DateUtils.getCalendarDateString(yesterday18YearsAgo)).withSignedOn(UNIX_TIMESTAMP)
                .build();
        consentService.consentToResearch(study, testUser.getUser(), sig, sharingScope, false);
        consentService.withdrawConsent(study, testUser.getUser());

        // But this is not, one day to go
        try {
            sig = new ConsentSignature.Builder().withName("Test User")
                    .withBirthdate(DateUtils.getCalendarDateString(tomorrow18YearsAgo)).withSignedOn(UNIX_TIMESTAMP)
                    .build();
            consentService.consentToResearch(study, testUser.getUser(), sig, sharingScope, false);
            fail("This should throw an exception");
        } catch (InvalidEntityException e) {
            consentService.withdrawConsent(study, testUser.getUser());
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
        ConsentSignature researchConsent = new ConsentSignature.Builder().withName("John Smith")
                .withBirthdate("1990-11-11").withSignedOn(UNIX_TIMESTAMP).build();
        consentService.consentToResearch(testUser.getStudy(), testUser.getUser(), researchConsent,
                SharingScope.SPONSORS_AND_PARTNERS, false);

        assertTrue("Should be consented",
                consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        assertTrue("Should have signed most recent consent.",
                consentService.hasUserSignedMostRecentConsent(testUser.getStudy(), testUser.getUser()));

        // Create new study consent, but do not activate it. User is consented and has still signed most recent consent.
        StudyConsent newStudyConsent = studyConsentService.addConsent(testUser.getStudyIdentifier(), defaultConsentDocument).getStudyConsent();

        assertTrue("Should be consented.",
                consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        assertTrue("Should have signed most recent consent, even though new consent is added.",
                consentService.hasUserSignedMostRecentConsent(testUser.getStudy(), testUser.getUser()));

        // Activate new study consent. User is consented and but has not signed most recent consent.
        newStudyConsent = studyConsentService.publishConsent(study, newStudyConsent.getCreatedOn()).getStudyConsent();

        assertTrue("Should still be consented.",
                consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        assertFalse("New consent activated. Should no longer have signed most recent consent. ",
                consentService.hasUserSignedMostRecentConsent(testUser.getStudy(), testUser.getUser()));

        // To consent again, first need to withdraw. User is consented and has now signed most recent consent.
        consentService.withdrawConsent(testUser.getStudy(), testUser.getUser());
        consentService.consentToResearch(testUser.getStudy(), testUser.getUser(), researchConsent,
                SharingScope.SPONSORS_AND_PARTNERS, false);

        assertTrue("Should still be consented.",
                consentService.hasUserConsentedToResearch(testUser.getStudy(), testUser.getUser()));
        assertTrue("Should again have signed most recent consent.",
                consentService.hasUserSignedMostRecentConsent(testUser.getStudy(), testUser.getUser()));
    }
    
}