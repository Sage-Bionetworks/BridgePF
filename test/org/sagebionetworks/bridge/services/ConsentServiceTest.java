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
import java.util.Map;

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
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
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
public class ConsentServiceTest {
    
    private static final Long UNIX_TIMESTAMP = DateTime.now().getMillis();
    private static final Withdrawal WITHDRAWAL = new Withdrawal("For reasons.");
    
    @Resource
    private ConsentService consentService;
    
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
    
    private Subpopulation defaultSubpopulation;
    
    private CriteriaContext context;
    
    @Value("classpath:study-defaults/consent-body.xhtml")
    public void setDefaultConsentDocument(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultConsentDocument = new StudyConsentForm(IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8));
    }
    
    private Study study;
    
    private TestUser testUser;

    @Before
    public void before() {
        study = TestUtils.getValidStudy(ConsentServiceTest.class);
        study = studyService.createStudy(study);
        
        // Default is always created, so use it for this test.
        defaultSubpopulation = subpopService.getSubpopulations(study).get(0);
        
        testUser = helper.getBuilder(ConsentServiceTest.class).withStudy(study).withConsent(false).build();
        
        context = new CriteriaContext.Builder()
                .withHealthCode(testUser.getUser().getHealthCode())
                .withStudyIdentifier(testUser.getStudyIdentifier())
                .withUserDataGroups(testUser.getUser().getDataGroups()).build();
    }

    @After
    public void after() {
        try {
            helper.deleteUser(testUser);    
        } finally {
            studyService.deleteStudy(study.getIdentifier());    
        }
    }

    @Test
    public void userHasNotGivenConsent() {
        // These are all the consents that apply to the user, and none of them are signed
        Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
        assertNotConsented(statuses);
        
        List<UserConsentHistory> histories = consentService.getUserConsentHistory(testUser.getStudy(),
                defaultSubpopulation.getGuid(), testUser.getUser());
        assertTrue(histories.isEmpty());
        
        try {
            consentService.getConsentSignature(testUser.getStudy(), defaultSubpopulation.getGuid(), testUser.getUser());
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
        
        // Before consent if you ask, no sharing
        SharingScope scope = optionsService.getOptions(testUser.getUser().getHealthCode()).getEnum(SHARING_SCOPE, SharingScope.class);
        assertEquals(SharingScope.NO_SHARING, scope);
        
        consentService.consentToResearch(testUser.getStudy(), defaultSubpopulation.getGuid(), testUser.getUser(), signature, SharingScope.ALL_QUALIFIED_RESEARCHERS, false);
        
        // Verify we just set the options
        scope = optionsService.getOptions(testUser.getUser().getHealthCode()).getEnum(SHARING_SCOPE, SharingScope.class);
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, scope);
        
        Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
        assertEquals(1, statuses.size());
        assertTrue(ConsentStatus.isUserConsented(statuses));

        ConsentSignature returnedSig = consentService.getConsentSignature(testUser.getStudy(), defaultSubpopulation.getGuid(), testUser.getUser());
        assertEquals("Eggplant McTester", returnedSig.getName());
        assertEquals("1970-01-01", returnedSig.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, returnedSig.getImageData());
        assertEquals("image/fake", returnedSig.getImageMimeType());
        assertEquals(signedOn, returnedSig.getSignedOn());

        // Withdraw consent and verify.
        consentService.withdrawConsent(testUser.getStudy(), defaultSubpopulation.getGuid(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);
        
        // No longer consented.
        statuses = consentService.getConsentStatuses(context);
        assertFalse(ConsentStatus.isUserConsented(statuses));
        
        // No more sharing status
        scope = optionsService.getOptions(testUser.getUser().getHealthCode()).getEnum(SHARING_SCOPE, SharingScope.class);
        assertEquals(SharingScope.NO_SHARING, scope);
        
        // Consent signature is no longer found, it's effectively deleted
        try {
            consentService.getConsentSignature(testUser.getStudy(), defaultSubpopulation.getGuid(), testUser.getUser());
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
        }
        
        // However we have a historical record of the consent, including a revocation date
        // data is exported with the sharing status set at the time it was exported
        List<UserConsentHistory> histories = consentService.getUserConsentHistory(testUser.getStudy(),
                defaultSubpopulation.getGuid(), testUser.getUser());
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
        consentService.consentToResearch(study, defaultSubpopulation.getGuid(), testUser.getUser(), sig, sharingScope, false);

        consentService.withdrawConsent(study, defaultSubpopulation.getGuid(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);

        // Also okay
        sig = new ConsentSignature.Builder().withName("Test User")
                .withBirthdate(DateUtils.getCalendarDateString(yesterday18YearsAgo)).build();
        consentService.consentToResearch(study, defaultSubpopulation.getGuid(), testUser.getUser(), sig, sharingScope, false);
        consentService.withdrawConsent(study, defaultSubpopulation.getGuid(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);

        // But this is not, one day to go
        try {
            sig = new ConsentSignature.Builder().withName("Test User")
                    .withBirthdate(DateUtils.getCalendarDateString(tomorrow18YearsAgo)).build();
            consentService.consentToResearch(study, defaultSubpopulation.getGuid(), testUser.getUser(), sig, sharingScope, false);
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

        consentService.consentToResearch(testUser.getStudy(), defaultSubpopulation.getGuid(), testUser.getUser(), consent,
                SharingScope.SPONSORS_AND_PARTNERS, false);

        // There are two places where you can get statuses, the original service, or the user. Verify both each time
        assertConsented(consentService.getConsentStatuses(context), true);
        assertConsented(testUser.getUser().getConsentStatuses(), true);

        // Create new study consent, but do not activate it. User is consented and has still signed most recent consent.
        StudyConsent newStudyConsent = studyConsentService.addConsent(defaultSubpopulation.getGuid(), defaultConsentDocument)
                .getStudyConsent();

        assertConsented(consentService.getConsentStatuses(context), true);
        assertConsented(testUser.getUser().getConsentStatuses(), true);

        // Public the new study consent. User is consented and but has no longer signed the most recent consent.
        newStudyConsent = studyConsentService
                .publishConsent(study, defaultSubpopulation.getGuid(), newStudyConsent.getCreatedOn()).getStudyConsent();

        // We need to manually update because the users consent status won't change due to changes in consents 
        // or subpopulations. Not until the session is refreshed. 
        testUser.getUser().setConsentStatuses(consentService.getConsentStatuses(context));
        
        assertConsented(consentService.getConsentStatuses(context), false);
        assertConsented(testUser.getUser().getConsentStatuses(), false);

        // To consent again, first need to withdraw. User is consented and has now signed most recent consent.
        consentService.withdrawConsent(testUser.getStudy(), defaultSubpopulation.getGuid(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);
        
        consentService.consentToResearch(testUser.getStudy(), defaultSubpopulation.getGuid(), testUser.getUser(),
            new ConsentSignature.Builder().withConsentSignature(consent).withSignedOn(DateTime.now().getMillis()).build(),
            SharingScope.SPONSORS_AND_PARTNERS, false);

        assertConsented(consentService.getConsentStatuses(context), true);
        assertConsented(testUser.getUser().getConsentStatuses(), true);
    }
    
    @Test
    public void withdrawConsent() {
        long originalSignedOn = DateTime.now().getMillis();
        ConsentSignature consent = makeSignature(originalSignedOn);

        consentService.consentToResearch(testUser.getStudy(), defaultSubpopulation.getGuid(), testUser.getUser(), consent,
                SharingScope.SPONSORS_AND_PARTNERS, false);

        // Consent exists, user is consented
        Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
        assertConsented(statuses, true);
        assertNotNull(consentService.getConsentSignature(testUser.getStudy(), defaultSubpopulation.getGuid(), testUser.getUser()));
        
        // Now withdraw consent
        consentService.withdrawConsent(testUser.getStudy(), defaultSubpopulation.getGuid(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);
        
        // Now user is not consented
        statuses = consentService.getConsentStatuses(context);
        assertNotConsented(statuses);
        try {
            consentService.getConsentSignature(testUser.getStudy(), defaultSubpopulation.getGuid(), testUser.getUser());            
        } catch(EntityNotFoundException e) {
            assertEquals("ConsentSignature not found.", e.getMessage());
        }
        
        Account account = accountDao.getAccount(testUser.getStudy(), testUser.getEmail());
        assertEquals(1, account.getConsentSignatureHistory(defaultSubpopulation.getGuid()).size());
        ConsentSignature historicalSignature = account.getConsentSignatureHistory(defaultSubpopulation.getGuid()).get(0);
        
        assertEquals(consent.getName(), historicalSignature.getName());
        assertEquals(consent.getBirthdate(), historicalSignature.getBirthdate());
        assertEquals(consent.getSignedOn(), historicalSignature.getSignedOn());
        
        assertNull(account.getActiveConsentSignature(defaultSubpopulation.getGuid()));
        
        long newSignedOn = DateTime.now().getMillis();
        consent = new ConsentSignature.Builder().withConsentSignature(consent).withSignedOn(newSignedOn).build();
        consentService.consentToResearch(testUser.getStudy(), defaultSubpopulation.getGuid(), testUser.getUser(), consent,
                SharingScope.SPONSORS_AND_PARTNERS, false);
        
        // Finally, verify there is a history for this user.
        List<UserConsentHistory> history = consentService.getUserConsentHistory(testUser.getStudy(),
                defaultSubpopulation.getGuid(), testUser.getUser());
        assertEquals(2, history.size());
        
        UserConsentHistory withdrawnConsent = history.get(0);
        UserConsentHistory activeConsent = history.get(1);
        
        assertNotNull(withdrawnConsent.getWithdrewOn());
        assertNull(activeConsent.getWithdrewOn());
        
        StudyConsent studyConsent = studyConsentService.getActiveConsent(defaultSubpopulation.getGuid()).getStudyConsent();
        UserConsent userConsent = userConsentDao.getUserConsent(testUser.getUser().getHealthCode(),
                defaultSubpopulation.getGuid(), originalSignedOn);

        assertEquals(testUser.getUser().getHealthCode(), withdrawnConsent.getHealthCode());
        assertEquals(defaultSubpopulation.getGuidString(), withdrawnConsent.getSubpopulationGuid());
        assertEquals(studyConsent.getCreatedOn(), withdrawnConsent.getConsentCreatedOn());
        assertEquals(consent.getName(), withdrawnConsent.getName());
        assertEquals(consent.getBirthdate(), withdrawnConsent.getBirthdate());
        assertEquals(consent.getImageData(), withdrawnConsent.getImageData());
        assertEquals(consent.getImageMimeType(), withdrawnConsent.getImageMimeType());
        assertEquals(originalSignedOn, withdrawnConsent.getSignedOn());
        assertEquals(userConsent.getWithdrewOn(), withdrawnConsent.getWithdrewOn());
        assertEquals(true, withdrawnConsent.isHasSignedActiveConsent());
    }
    
    @Test
    public void userCanConsentAndUnconsentToDifferentSubpopulations() {
        // Create a second required subpopulation
        Subpopulation requiredSubpop = Subpopulation.create();
        requiredSubpop.setName("Genetics Contributors");
        requiredSubpop.setRequired(true);
        requiredSubpop = subpopService.createSubpopulation(study, requiredSubpop);
        
        // Create an optional subpopulation, it will have zero impact on this test... if all works correctly
        Subpopulation optionalSubpop = Subpopulation.create();
        optionalSubpop.setName("You Don't Really Have To Do This");
        optionalSubpop.setRequired(false);
        optionalSubpop = subpopService.createSubpopulation(study, optionalSubpop);
        
        // Users don't see consent status updates in their sessions when subpopulations are 
        // added. So we force that here for the tests. In production, sessions will have
        // to time or users will have to reconsent before these updates get picked up.
        testUser.getUser().setConsentStatuses(consentService.getConsentStatuses(context));
        
        Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
        assertEquals(3, statuses.size());
        assertNotConsented(statuses);
        
        consentService.consentToResearch(study, defaultSubpopulation.getGuid(), testUser.getUser(),
                makeSignature(DateTime.now().getMillis()), SharingScope.ALL_QUALIFIED_RESEARCHERS, false);
        
        statuses = consentService.getConsentStatuses(context);
        assertNotConsented(statuses);
        
        consentService.consentToResearch(study, requiredSubpop.getGuid(), testUser.getUser(),
                makeSignature(DateTime.now().getMillis()), SharingScope.ALL_QUALIFIED_RESEARCHERS, false);        
        
        statuses = consentService.getConsentStatuses(context);
        assertConsented(statuses, true);
        
        consentService.withdrawConsent(study, defaultSubpopulation.getGuid(), testUser.getUser(), WITHDRAWAL,
                DateTime.now().getMillis());
        
        // your sharing has been turned off because not all required consents are signed
        SharingScope scope = optionsService.getOptions(testUser.getUser().getHealthCode()).getEnum(SHARING_SCOPE, SharingScope.class);
        assertEquals(SharingScope.NO_SHARING, scope);
        
        statuses = consentService.getConsentStatuses(context);
        assertNotConsented(statuses);
        assertFalse(statuses.get(defaultSubpopulation.getGuid()).isConsented());
        assertTrue(statuses.get(requiredSubpop.getGuid()).isConsented());
        assertFalse(statuses.get(optionalSubpop.getGuid()).isConsented());
        // Just verify that it now doesn't appear to exist, so this is an exception
        try {
            consentService.withdrawConsent(study, defaultSubpopulation.getGuid(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        
        consentService.withdrawConsent(study, requiredSubpop.getGuid(), testUser.getUser(), WITHDRAWAL, UNIX_TIMESTAMP);
        
        statuses = consentService.getConsentStatuses(context);
        assertNotConsented(statuses);
        assertFalse(statuses.get(defaultSubpopulation.getGuid()).isConsented());
        assertFalse(statuses.get(requiredSubpop.getGuid()).isConsented());
        assertFalse(statuses.get(optionalSubpop.getGuid()).isConsented()); // still false
    }
    
    private void assertConsented(Map<SubpopulationGuid,ConsentStatus> statuses, boolean signedMostRecent) {
        assertTrue(ConsentStatus.isUserConsented(statuses));
        assertTrue(testUser.getUser().doesConsent());
        assertTrue(ConsentStatus.isUserConsented(testUser.getUser().getConsentStatuses()));
        if (signedMostRecent) {
            assertTrue(ConsentStatus.isConsentCurrent(statuses));
            assertTrue(testUser.getUser().hasSignedMostRecentConsent());
        } else {
            assertFalse(ConsentStatus.isConsentCurrent(statuses));
            assertFalse(testUser.getUser().hasSignedMostRecentConsent());
        }
    }
    
    private void assertNotConsented(Map<SubpopulationGuid,ConsentStatus> statuses) {
        assertFalse(ConsentStatus.isUserConsented(statuses));
        assertFalse(testUser.getUser().doesConsent());
        assertFalse(ConsentStatus.isUserConsented(testUser.getUser().getConsentStatuses()));
    }
    
    private ConsentSignature makeSignature(long originalSignedOn) {
        ConsentSignature consent = new ConsentSignature.Builder().withName("John Smith")
                .withImageData("data").withImageMimeType("image/png").withBirthdate("1990-11-11")
                .withSignedOn(originalSignedOn).build();
        return consent;
    }
}