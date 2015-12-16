package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ConsentServiceImplTest {
    
    private static final Long UNIX_TIMESTAMP = DateTime.now().getMillis();
    private static final Withdrawal WITHDRAWAL = new Withdrawal("For reasons.");
    
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
    private SubpopulationService subpopService;
    
    @Resource
    private TestUserAdminHelper helper;

    private StudyConsentForm defaultConsentDocument;
    
    private Subpopulation subpopulation;
    
    private ScheduleContext context;
    
    @Value("classpath:study-defaults/consent-body.xhtml")
    public void setDefaultConsentDocument(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultConsentDocument = new StudyConsentForm(IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8));
    }
    
    private Study study;
    
    private TestUser testUser;

    @Before
    public void before() {
        study = TestUtils.getValidStudy(ConsentServiceImplTest.class);
        study = studyService.createStudy(study);
        
        // Test all of consent with a subpopulation that's not the default.
        subpopulation = Subpopulation.create();
        subpopulation.setName("Genetics Contributors");
        subpopulation.setRequired(true);
        subpopulation = subpopService.createSubpopulation(study, subpopulation);
        
        testUser = helper.getBuilder(ConsentServiceImplTest.class).withStudy(study).withConsent(false).build();
        
        context = new ScheduleContext.Builder()
            .withStudyIdentifier(study)
            .withHealthCode(testUser.getUser().getHealthCode()).build();
    }

    @After
    public void after() {
        helper.deleteUser(testUser);
        studyService.deleteStudy(study.getIdentifier());
    }

    @Test
    public void userHasNotGivenConsent() {
        // These are all the consents that apply to the user, and none of them are signed
        List<ConsentStatus> statuses = consentService.getConsentStatuses(context);
        assertFalse(ConsentStatus.isUserConsented(statuses));
        
        List<UserConsentHistory> histories = consentService.getUserConsentHistory(testUser.getStudy(),
                subpopulation.getGuid(), testUser.getUser());
        assertTrue(histories.isEmpty());
        
        try {
            consentService.getConsentSignature(testUser.getStudy(), subpopulation.getGuid(), testUser.getUser());
            fail("expected exception");
        } catch (EntityNotFoundException e) {
        }
    }
    
    @Test
    public void canGiveAndWithdrawConsent() {
        // Consent and verify, using signature image as well as other information. This combines information 
        // with another give/withdraw consent test that was redundant.
        ConsentSignature signature = new ConsentSignature.Builder().withName("Eggplant McTester")
                .withBirthdate("1970-01-01").withImageData(TestConstants.DUMMY_IMAGE_DATA)
                .withImageMimeType("image/fake").build();
        
        long signedOn = signature.getSignedOn();
        
        consentService.consentToResearch(testUser.getStudy(), subpopulation.getGuid(), testUser.getUser(), signature, SharingScope.NO_SHARING, false);
        
        List<ConsentStatus> statuses = consentService.getConsentStatuses(context);
        assertFalse(statuses.isEmpty());

        ConsentSignature returnedSig = consentService.getConsentSignature(testUser.getStudy(), subpopulation.getGuid(), testUser.getUser());
        assertEquals("Eggplant McTester", returnedSig.getName());
        assertEquals("1970-01-01", returnedSig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, returnedSig.getImageData());
        assertEquals("image/fake", returnedSig.getImageMimeType());
        assertEquals(signedOn, returnedSig.getSignedOn());

        // Withdraw consent and verify.
        consentService.withdrawConsent(testUser.getStudy(), subpopulation.getGuid(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);
        
        // No longer consented.
        statuses = consentService.getConsentStatuses(context);
        assertFalse(ConsentStatus.isUserConsented(statuses));
        
        // Consent signature is no longer found, it's effectively deleted
        try {
            consentService.getConsentSignature(testUser.getStudy(), subpopulation.getGuid(), testUser.getUser());
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
        }
        
        // However we have a historical record of the consent, including a revocation date
        List<UserConsentHistory> histories = consentService.getUserConsentHistory(testUser.getStudy(),
                subpopulation.getGuid(), testUser.getUser());
        assertEquals(1, histories.size());
        assertNotNull(histories.get(0).getWithdrewOn());
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
        consentService.consentToResearch(study, subpopulation.getGuid(), testUser.getUser(), sig, sharingScope, false);

        consentService.withdrawConsent(study, subpopulation.getGuid(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);

        // Also okay
        sig = new ConsentSignature.Builder().withName("Test User")
                .withBirthdate(DateUtils.getCalendarDateString(yesterday18YearsAgo)).build();
        consentService.consentToResearch(study, subpopulation.getGuid(), testUser.getUser(), sig, sharingScope, false);
        consentService.withdrawConsent(study, subpopulation.getGuid(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);

        // But this is not, one day to go
        try {
            sig = new ConsentSignature.Builder().withName("Test User")
                    .withBirthdate(DateUtils.getCalendarDateString(tomorrow18YearsAgo)).build();
            consentService.consentToResearch(study, subpopulation.getGuid(), testUser.getUser(), sig, sharingScope, false);
            fail("This should throw an exception");
        } catch (InvalidEntityException e) {
            assertTrue(e.getMessage().contains("years of age or older"));
        }
    }

    @Test
    public void checkConsentUpToDate() {
        // Working here with a consent stream in a different population than the default population for the 
        // study. 
        ConsentSignature consent = new ConsentSignature.Builder().withName("John Smith")
                .withBirthdate("1990-11-11").build();

        consentService.consentToResearch(testUser.getStudy(), subpopulation.getGuid(), testUser.getUser(), consent,
                SharingScope.SPONSORS_AND_PARTNERS, false);

        ConsentStatus status = ConsentStatus.forSubpopulation(consentService.getConsentStatuses(context), subpopulation.getGuid());
        assertTrue("Should be consented", status.isConsented());
        assertTrue("Should have signed most recent consent.", status.getSignedMostRecentConsent());

        // Create new study consent, but do not activate it. User is consented and has still signed most recent consent.
        StudyConsent newStudyConsent = studyConsentService.addConsent(subpopulation.getGuid(), defaultConsentDocument)
                .getStudyConsent();

        status = ConsentStatus.forSubpopulation(consentService.getConsentStatuses(context), subpopulation.getGuid());
        assertTrue("Should be consented", status.isConsented());
        assertTrue("Still most recent consent", status.getSignedMostRecentConsent());

        // Public the new study consent. User is consented and but has no longer signed the most recent consent.
        newStudyConsent = studyConsentService
                .publishConsent(study, subpopulation.getGuid(), newStudyConsent.getCreatedOn()).getStudyConsent();

        status = ConsentStatus.forSubpopulation(consentService.getConsentStatuses(context), subpopulation.getGuid());
        assertTrue("Should be consented", status.isConsented());
        assertFalse("New consent activated. Should no longer have signed most recent consent. ", status.getSignedMostRecentConsent());

        // To consent again, first need to withdraw. User is consented and has now signed most recent consent.
        consentService.withdrawConsent(testUser.getStudy(), subpopulation.getGuid(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);
        
        consentService.consentToResearch(testUser.getStudy(), subpopulation.getGuid(), testUser.getUser(),
            new ConsentSignature.Builder().withConsentSignature(consent).withSignedOn(DateTime.now().getMillis()).build(),
            SharingScope.SPONSORS_AND_PARTNERS, false);

        status = ConsentStatus.forSubpopulation(consentService.getConsentStatuses(context), subpopulation.getGuid());
        assertTrue("Should still be consented.", status.isConsented());
        assertTrue("Should again have signed most recent consent.", status.getSignedMostRecentConsent());
    }
    
    @Test
    public void withdrawConsent() {
        long originalSignedOn = DateTime.now().getMillis();
        ConsentSignature consent = makeSignature(originalSignedOn);

        consentService.consentToResearch(testUser.getStudy(), subpopulation.getGuid(), testUser.getUser(), consent,
                SharingScope.SPONSORS_AND_PARTNERS, false);

        // Consent exists, user is consented
        ConsentStatus status = consentService.getConsentStatuses(context).get(0);
        assertTrue(status.isConsented());
        assertNotNull(consentService.getConsentSignature(testUser.getStudy(), subpopulation.getGuid(), testUser.getUser()));
        
        // Now withdraw consent
        consentService.withdrawConsent(testUser.getStudy(), subpopulation.getGuid(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);
        
        // Now user is not consented
        status = consentService.getConsentStatuses(context).get(0);
        assertFalse(status.isConsented());
        try {
            consentService.getConsentSignature(testUser.getStudy(), subpopulation.getGuid(), testUser.getUser());            
        } catch(EntityNotFoundException e) {
            assertEquals("ConsentSignature not found.", e.getMessage());
        }
        
        Account account = accountDao.getAccount(testUser.getStudy(), testUser.getEmail());
        assertEquals(1, account.getConsentSignatureHistory(subpopulation.getGuid()).size());
        ConsentSignature historicalSignature = account.getConsentSignatureHistory(subpopulation.getGuid()).get(0);
        
        assertEquals(consent.getName(), historicalSignature.getName());
        assertEquals(consent.getBirthdate(), historicalSignature.getBirthdate());
        assertEquals(consent.getSignedOn(), historicalSignature.getSignedOn());
        
        assertNull(account.getActiveConsentSignature(subpopulation.getGuid()));
        
        long newSignedOn = DateTime.now().getMillis();
        consent = new ConsentSignature.Builder().withConsentSignature(consent).withSignedOn(newSignedOn).build();
        consentService.consentToResearch(testUser.getStudy(), subpopulation.getGuid(), testUser.getUser(), consent,
                SharingScope.SPONSORS_AND_PARTNERS, false);
        
        // Finally, verify there is a history for this user.
        List<UserConsentHistory> history = consentService.getUserConsentHistory(testUser.getStudy(),
                subpopulation.getGuid(), testUser.getUser());
        assertEquals(2, history.size());
        
        UserConsentHistory withdrawnConsent = history.get(0);
        UserConsentHistory activeConsent = history.get(1);
        
        assertNotNull(withdrawnConsent.getWithdrewOn());
        assertNull(activeConsent.getWithdrewOn());
        
        StudyConsent studyConsent = studyConsentService.getActiveConsent(subpopulation.getGuid()).getStudyConsent();
        UserConsent userConsent = userConsentDao.getUserConsent(testUser.getUser().getHealthCode(),
                subpopulation.getGuid(), originalSignedOn);

        assertEquals(testUser.getUser().getHealthCode(), withdrawnConsent.getHealthCode());
        assertEquals(subpopulation.getGuidString(), withdrawnConsent.getSubpopulationGuid());
        assertEquals(studyConsent.getCreatedOn(), withdrawnConsent.getConsentCreatedOn());
        assertEquals(consent.getName(), withdrawnConsent.getName());
        assertEquals(consent.getBirthdate(), withdrawnConsent.getBirthdate());
        assertEquals(consent.getImageData(), withdrawnConsent.getImageData());
        assertEquals(consent.getImageMimeType(), withdrawnConsent.getImageMimeType());
        assertEquals(originalSignedOn, withdrawnConsent.getSignedOn());
        assertEquals(userConsent.getWithdrewOn(), withdrawnConsent.getWithdrewOn());
        assertEquals(true, withdrawnConsent.isHasSignedActiveConsent());
    }

    private ConsentSignature makeSignature(long originalSignedOn) {
        ConsentSignature consent = new ConsentSignature.Builder().withName("John Smith")
                .withImageData("data").withImageMimeType("image/png").withBirthdate("1990-11-11")
                .withSignedOn(originalSignedOn).build();
        return consent;
    }
    
    @Test
    public void userCanConsentAndUnconsentToDifferentSubpopulations() {
        // Make an optional subpopulation, it will have zero impact on this test... if all works correctly
        Subpopulation optionalSubpop = Subpopulation.create();
        optionalSubpop.setName("You Don't Really Have To Do This");
        optionalSubpop.setRequired(false);
        optionalSubpop = subpopService.createSubpopulation(study, optionalSubpop);
        
        // Get the other subpopulation
        Subpopulation defaultSubpop = subpopService.getSubpopulation(study, SubpopulationGuid.create(study.getIdentifier()));

        List<ConsentStatus> statuses = consentService.getConsentStatuses(context);
        assertEquals(3, statuses.size());
        assertFalse(ConsentStatus.isUserConsented(statuses));
        
        consentService.consentToResearch(study, defaultSubpop.getGuid(), testUser.getUser(),
                makeSignature(DateTime.now().getMillis()), SharingScope.NO_SHARING, false);
        
        statuses = consentService.getConsentStatuses(context);
        assertFalse(ConsentStatus.isUserConsented(statuses));
        
        consentService.consentToResearch(study, subpopulation.getGuid(), testUser.getUser(),
                makeSignature(DateTime.now().getMillis()), SharingScope.NO_SHARING, false);        
        
        statuses = consentService.getConsentStatuses(context);
        assertTrue(ConsentStatus.isUserConsented(statuses)); // despite not signing the optional subpop
        
        consentService.withdrawConsent(study, defaultSubpop.getGuid(), testUser.getUser(), new Withdrawal("Nada"),
                DateTime.now().getMillis());
        
        statuses = consentService.getConsentStatuses(context);
        assertFalse(ConsentStatus.isUserConsented(statuses));
        assertFalse(ConsentStatus.forSubpopulation(statuses, defaultSubpop.getGuid()).isConsented());
        assertTrue(ConsentStatus.forSubpopulation(statuses, subpopulation.getGuid()).isConsented());
        assertFalse(ConsentStatus.forSubpopulation(statuses, optionalSubpop.getGuid()).isConsented());
        
        // Just verify that it now doesn't appear to exist, so this is an exception
        try {
            consentService.withdrawConsent(study, defaultSubpop.getGuid(), testUser.getUser(), new Withdrawal("Nada"),
                    DateTime.now().getMillis());
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        
        consentService.withdrawConsent(study, subpopulation.getGuid(), testUser.getUser(), new Withdrawal("Nada"),
                DateTime.now().getMillis());
        
        statuses = consentService.getConsentStatuses(context);
        assertFalse(ConsentStatus.isUserConsented(statuses));
        assertFalse(ConsentStatus.forSubpopulation(statuses, defaultSubpop.getGuid()).isConsented());
        assertFalse(ConsentStatus.forSubpopulation(statuses, subpopulation.getGuid()).isConsented());
        assertFalse(ConsentStatus.forSubpopulation(statuses, optionalSubpop.getGuid()).isConsented()); // still false
    }
}