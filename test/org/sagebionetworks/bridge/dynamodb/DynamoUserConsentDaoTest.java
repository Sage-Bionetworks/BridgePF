package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoUserConsentDaoTest {

    private static final String HEALTH_CODE = "hc789";
    private static final StudyIdentifier STUDY_IDENTIFIER = new StudyIdentifierImpl("study123");

    @Resource
    private DynamoUserConsentDao userConsentDao;

    @BeforeClass
    public static void beforeClass() {
        DynamoInitializer.init(DynamoUserConsent3.class);
    }
    
    @Before
    public void before() {
        userConsentDao.deleteAllConsents(HEALTH_CODE, STUDY_IDENTIFIER);
        for (int i=1; i < 6; i++) {
            userConsentDao.deleteAllConsents(HEALTH_CODE+i, STUDY_IDENTIFIER);
        }
    }

    @After
    public void after() {
        userConsentDao.deleteAllConsents(HEALTH_CODE, STUDY_IDENTIFIER);
        for (int i=1; i < 6; i++) {
            userConsentDao.deleteAllConsents(HEALTH_CODE+i, STUDY_IDENTIFIER);
        }
    }
    
    @Test
    public void canConsentToStudy() {
        final DynamoStudyConsent1 consent = createStudyConsent();
        verifyActiveConsentAbsent();
        verifyConsentAbsentAt(DateTime.now().getMillis()); // some random time will fail
        
        // Give consent
        long signedOn = DateTime.now().getMillis();
        userConsentDao.giveConsent(HEALTH_CODE, consent, signedOn);
        verifyActiveConsentExists();

        // Withdraw
        userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER, DateTime.now().getMillis());
        verifyActiveConsentAbsent();
        verifyConsentExistsAt(signedOn);
        
        // There should be no consent to withdraw from (even though it's in the historical list)
        try {
            userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER, DateTime.now().getMillis());
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals("Consent not found.", e.getMessage());
        }

        // Can give consent again because the previous consent is withdrawn
        long signedAgainOn = DateTime.now().getMillis();
        userConsentDao.giveConsent(HEALTH_CODE, consent, signedAgainOn);
        verifyActiveConsentExists();
        verifyConsentExistsAt(signedAgainOn);
        verifyConsentExistsAt(signedOn); // this is still also true.
        
        // Withdraw again
        userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER, DateTime.now().getMillis());
        verifyActiveConsentAbsent();
    }

    @Test
    public void canCountStudyParticipants() throws Exception {
        final DynamoStudyConsent1 consent = createStudyConsent();
        for (int i=1; i < 6; i++) {
            userConsentDao.giveConsent(HEALTH_CODE+i, consent, DateTime.now().getMillis());
        }
        
        long count = userConsentDao.getNumberOfParticipants(STUDY_IDENTIFIER);
        assertEquals("Correct number of participants", 5, count);
        
        userConsentDao.withdrawConsent(HEALTH_CODE+"5", STUDY_IDENTIFIER, DateTime.now().getMillis());
        count = userConsentDao.getNumberOfParticipants(STUDY_IDENTIFIER);
        assertEquals("Correct number of participants", 4, count);
        
        userConsentDao.giveConsent(HEALTH_CODE+"5", consent, DateTime.now().getMillis());
        count = userConsentDao.getNumberOfParticipants(STUDY_IDENTIFIER);
        assertEquals("Correct number of participants", 5, count);
    }
    
    @Test
    public void canGetAllConsentsInHistory() throws Exception {
        final DynamoStudyConsent1 consent = createStudyConsent();
        final StudyIdentifier studyId = new StudyIdentifierImpl(consent.getStudyKey());
        for (int i=0; i < 5; i++) {
            userConsentDao.giveConsent(HEALTH_CODE, consent, DateTime.now().getMillis());
            userConsentDao.withdrawConsent(HEALTH_CODE, studyId, DateTime.now().getMillis());
        }
        userConsentDao.giveConsent(HEALTH_CODE, consent, DateTime.now().getMillis());
        
        List<UserConsent> consents = userConsentDao.getUserConsentHistory(HEALTH_CODE, studyId);
        assertEquals(6, consents.size());
        for (int i=0; i < 5; i++) {
            assertNotNull(consents.get(i).getWithdrewOn());
        }
        assertNull(consents.get(consents.size()-1).getWithdrewOn());
    }

    @Test
    public void singleUserCanSignMultipleConsents() {
        DynamoStudyConsent1 consent = createStudyConsent(DateTime.now().getMillis());

        long signedOn = DateTime.now().getMillis();
        userConsentDao.giveConsent(HEALTH_CODE, consent, signedOn);
        
        // But you cannot sign the same study a second time
        try {
            userConsentDao.giveConsent(HEALTH_CODE, consent, signedOn);
        } catch(BridgeServiceException e) {
            assertEquals(409, e.getStatusCode());
            assertEquals("Consent already exists.", e.getMessage());
        }
        
        // Now create a different study consent, you should be able to consent again.
        long secondConsentCreatedOn = DateTime.now().getMillis();
        consent = createStudyConsent(secondConsentCreatedOn);
        long signedOn2 = signedOn + (100000);
        userConsentDao.giveConsent(HEALTH_CODE, consent, signedOn2);
        
        List<UserConsent> consents = userConsentDao.getUserConsentHistory(HEALTH_CODE, STUDY_IDENTIFIER);
        assertEquals(2, consents.size());
        assertEquals(signedOn, consents.get(0).getSignedOn());
        assertEquals(signedOn2, consents.get(1).getSignedOn());
        
        // The active consent is the second signed consent.
        UserConsent activeConsent = userConsentDao.getActiveUserConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertEquals(signedOn2, activeConsent.getSignedOn());
        assertEquals(secondConsentCreatedOn, activeConsent.getConsentCreatedOn());
    }
    
    @Test
    public void getConsentBySignedOnDate() throws Exception {
        final DynamoStudyConsent1 studyConsent = createStudyConsent();
        
        final long signedOn = DateTime.now().getMillis();
        userConsentDao.giveConsent(HEALTH_CODE, studyConsent, signedOn);
        
        UserConsent consent = userConsentDao.getUserConsent(HEALTH_CODE, STUDY_IDENTIFIER, signedOn);
        assertEquals(signedOn, consent.getSignedOn());
        assertEquals(STUDY_IDENTIFIER.getIdentifier(), consent.getStudyIdentifier());
        assertNull(consent.getWithdrewOn());
        
        // Withdraw consent, you can still retrieve
        final long withdrewOn = DateTime.now().getMillis();
        userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER, withdrewOn);
        
        consent = userConsentDao.getUserConsent(HEALTH_CODE, STUDY_IDENTIFIER, signedOn);
        assertEquals(signedOn, consent.getSignedOn());
        assertEquals(STUDY_IDENTIFIER.getIdentifier(), consent.getStudyIdentifier());
        assertEquals((Long)withdrewOn, consent.getWithdrewOn());
    }
    
    private void verifyActiveConsentExists() {
        assertNotNull(userConsentDao.getActiveUserConsent(HEALTH_CODE, STUDY_IDENTIFIER));
        assertTrue(userConsentDao.hasConsented(HEALTH_CODE, STUDY_IDENTIFIER));
    }
    
    private void verifyActiveConsentAbsent() {
        assertNull(userConsentDao.getActiveUserConsent(HEALTH_CODE, STUDY_IDENTIFIER));
        assertFalse(userConsentDao.hasConsented(HEALTH_CODE, STUDY_IDENTIFIER));
    }
    
    private void verifyConsentExistsAt(long signedOn) {
        UserConsent consent = userConsentDao.getUserConsent(HEALTH_CODE, STUDY_IDENTIFIER, signedOn);
        assertNotNull(consent);
        assertEquals(signedOn, consent.getSignedOn());
    }
    
    private void verifyConsentAbsentAt(long signedOn) {
        try {
            userConsentDao.getUserConsent(HEALTH_CODE, STUDY_IDENTIFIER, signedOn);
            fail("Should have thrown an exception");
        } catch(BridgeServiceException e) {
            assertEquals(404, e.getStatusCode());
        }
    }
    
    private DynamoStudyConsent1 createStudyConsent(long createdOn) {
        final DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(STUDY_IDENTIFIER.getIdentifier());
        consent.setCreatedOn(createdOn);
        return consent;
    }
    
    private DynamoStudyConsent1 createStudyConsent() {
        return createStudyConsent(123L);
    }
}
