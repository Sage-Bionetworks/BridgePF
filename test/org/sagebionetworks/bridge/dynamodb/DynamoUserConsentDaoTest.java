package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoUserConsentDaoTest {

    private static final String HEALTH_CODE = "hc789";
    private static final String STUDY_IDENTIFIER = "study123";
    @Resource
    private DynamoUserConsentDao userConsentDao;

    @BeforeClass
    public static void initialSetUp() {
        DynamoInitializer.init(DynamoUserConsent2.class);
        DynamoTestUtil.clearTable(DynamoUserConsent2.class);
    }

    @AfterClass
    public static void finalCleanUp() {
        DynamoTestUtil.clearTable(DynamoUserConsent2.class);
    }

    @Test
    public void canConsentToStudy() {
        // Not consented yet
        final DynamoStudyConsent1 consent = createStudyConsent();
        assertFalse(userConsentDao.hasConsented(HEALTH_CODE, consent));
        assertFalse(userConsentDao.hasConsented2(HEALTH_CODE, consent));
        assertNull(userConsentDao.getConsentCreatedOn(HEALTH_CODE, consent.getStudyKey()));

        // Give consent
        final ConsentSignature consentSignature = createConsentSignature();
        userConsentDao.giveConsent(HEALTH_CODE, consent, consentSignature);
        assertTrue(userConsentDao.hasConsented(HEALTH_CODE, consent));
        assertTrue(userConsentDao.hasConsented2(HEALTH_CODE, consent));
        assertEquals(Long.valueOf(123), userConsentDao.getConsentCreatedOn(HEALTH_CODE, consent.getStudyKey()));
        ConsentSignature cs = userConsentDao.getConsentSignature(HEALTH_CODE, consent);
        assertEquals(consentSignature.getName(), cs.getName());
        assertEquals(consentSignature.getBirthdate(), cs.getBirthdate());
        assertEquals(consentSignature.getImageData(), cs.getImageData());
        assertEquals(consentSignature.getImageMimeType(), cs.getImageMimeType());

        // Withdraw
        userConsentDao.withdrawConsent(HEALTH_CODE, consent);
        assertFalse(userConsentDao.hasConsented(HEALTH_CODE, consent));
        assertFalse(userConsentDao.hasConsented2(HEALTH_CODE, consent));

        // Can give consent again if the previous consent is withdrawn
        userConsentDao.giveConsent(HEALTH_CODE, consent, consentSignature);
        assertTrue(userConsentDao.hasConsented(HEALTH_CODE, consent));
        assertTrue(userConsentDao.hasConsented2(HEALTH_CODE, consent));

        // Withdraw again
        userConsentDao.withdrawConsent(HEALTH_CODE, consent);
        assertFalse(userConsentDao.hasConsented(HEALTH_CODE, consent));
        assertFalse(userConsentDao.hasConsented2(HEALTH_CODE, consent));

    }
    
    @Test
    public void canCountStudyParticipants() {
        final DynamoStudyConsent1 consent = createStudyConsent();
        final ConsentSignature consentSignature = createConsentSignature();
        
        for (int i=1; i < 6; i++) {
            userConsentDao.giveConsent(HEALTH_CODE+i, consent, consentSignature);
        }

        long count = userConsentDao.getNumberOfParticipants(STUDY_IDENTIFIER);
        assertEquals("Correct number of participants", 5, count);
        
        userConsentDao.withdrawConsent(HEALTH_CODE+"5", consent);
        count = userConsentDao.getNumberOfParticipants(STUDY_IDENTIFIER);
        assertEquals("Correct number of participants", 4, count);
        
        userConsentDao.giveConsent(HEALTH_CODE+"5", consent, consentSignature);
        count = userConsentDao.getNumberOfParticipants(STUDY_IDENTIFIER);
        assertEquals("Correct number of participants", 5, count);
    }

    private DynamoStudyConsent1 createStudyConsent() {
        final DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(STUDY_IDENTIFIER);
        consent.setCreatedOn(123L);
        return consent;
    }
    
    private ConsentSignature createConsentSignature() {
        return ConsentSignature.create("John Smith", "1999-12-01", TestConstants.DUMMY_IMAGE_DATA, "image/gif");
    }
}
