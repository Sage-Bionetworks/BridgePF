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
        // Not consented yet
        final DynamoStudyConsent1 consent = createStudyConsent();
        assertFalse(userConsentDao.hasConsented(HEALTH_CODE, new StudyIdentifierImpl(consent.getStudyKey())));

        // Give consent
        userConsentDao.giveConsent(HEALTH_CODE, consent, DateTime.now().getMillis());
        assertTrue(userConsentDao.hasConsented(HEALTH_CODE, new StudyIdentifierImpl(consent.getStudyKey())));

        // Withdraw
        userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertFalse(userConsentDao.hasConsented(HEALTH_CODE, new StudyIdentifierImpl(consent.getStudyKey())));
        
        // There should be no consent
        try {
            userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER);
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals("Consent not found.", e.getMessage());
        }
        
        UserConsent existingConsent = userConsentDao.getActiveUserConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertNull(existingConsent);

        // Can give consent again if the previous consent is withdrawn
        userConsentDao.giveConsent(HEALTH_CODE, consent, DateTime.now().getMillis());
        assertTrue(userConsentDao.hasConsented(HEALTH_CODE, new StudyIdentifierImpl(consent.getStudyKey())));

        // Now we can find it
        existingConsent = userConsentDao.getActiveUserConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertNotNull(existingConsent);
        
        // Withdraw again
        userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertFalse(userConsentDao.hasConsented(HEALTH_CODE, new StudyIdentifierImpl(consent.getStudyKey())));
    }

    @Test
    public void canCountStudyParticipants() throws Exception {
        final DynamoStudyConsent1 consent = createStudyConsent();
        for (int i=1; i < 6; i++) {
            userConsentDao.giveConsent(HEALTH_CODE+i, consent, DateTime.now().getMillis());
        }
        
        long count = userConsentDao.getNumberOfParticipants(STUDY_IDENTIFIER);
        assertEquals("Correct number of participants", 5, count);
        
        userConsentDao.withdrawConsent(HEALTH_CODE+"5", STUDY_IDENTIFIER);
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
            userConsentDao.withdrawConsent(HEALTH_CODE, studyId);
        }
        userConsentDao.giveConsent(HEALTH_CODE, consent, DateTime.now().getMillis());
        
        List<UserConsent> consents = userConsentDao.getUserConsentHistory(HEALTH_CODE, studyId);
        assertEquals(6, consents.size());
        for (int i=0; i < 5; i++) {
            assertNotNull(consents.get(i).getWithdrewOn());
        }
        assertNull(consents.get(consents.size()-1).getWithdrewOn());
    }

    private DynamoStudyConsent1 createStudyConsent() {
        final DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(STUDY_IDENTIFIER.getIdentifier());
        consent.setCreatedOn(123L);
        return consent;
    }
}
