package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
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

    @Before
    public void before() {
        DynamoInitializer.init(DynamoUserConsent2.class);
        DynamoTestUtil.clearTable(DynamoUserConsent2.class);
    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoUserConsent2.class);
    }

    @Test
    public void canConsentToStudy() {
        // Not consented yet
        final DynamoStudyConsent1 consent = createStudyConsent();
        assertFalse(userConsentDao.hasConsented2(HEALTH_CODE, consent));

        // Give consent
        userConsentDao.giveConsent(HEALTH_CODE, consent);
        assertTrue(userConsentDao.hasConsented2(HEALTH_CODE, consent));

        // Withdraw
        userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertFalse(userConsentDao.hasConsented2(HEALTH_CODE, consent));

        // Can give consent again if the previous consent is withdrawn
        userConsentDao.giveConsent(HEALTH_CODE, consent);
        assertTrue(userConsentDao.hasConsented2(HEALTH_CODE, consent));

        // Withdraw again
        userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertFalse(userConsentDao.hasConsented2(HEALTH_CODE, consent));

    }

    @Test
    public void canCountStudyParticipants() {
        final DynamoStudyConsent1 consent = createStudyConsent();
        for (int i=1; i < 6; i++) {
            userConsentDao.giveConsent(HEALTH_CODE+i, consent);
        }

        long count = userConsentDao.getNumberOfParticipants(STUDY_IDENTIFIER);
        assertEquals("Correct number of participants", 5, count);

        userConsentDao.withdrawConsent(HEALTH_CODE+"5", STUDY_IDENTIFIER);
        count = userConsentDao.getNumberOfParticipants(STUDY_IDENTIFIER);
        assertEquals("Correct number of participants", 4, count);

        userConsentDao.giveConsent(HEALTH_CODE+"5", consent);
        count = userConsentDao.getNumberOfParticipants(STUDY_IDENTIFIER);
        assertEquals("Correct number of participants", 5, count);
    }

    @Test
    public void testRemoveConsentSignature() {
        DynamoStudyConsent1 studyConsent = createStudyConsent();
        userConsentDao.giveConsent(HEALTH_CODE, studyConsent);
        DynamoUserConsent2 consent = (DynamoUserConsent2)userConsentDao.getUserConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        // Can "wipe out" nulls without causing exceptions
        userConsentDao.removeConsentSignature(HEALTH_CODE, STUDY_IDENTIFIER);
        // Add a consent signature
        ConsentSignature consentSignature = ConsentSignature.create(
                "John Smith", "1999-12-01", TestConstants.DUMMY_IMAGE_DATA, "image/gif");
        userConsentDao.putConsentSignature(HEALTH_CODE, STUDY_IDENTIFIER, consentSignature);
        consent = (DynamoUserConsent2)userConsentDao.getUserConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertEquals("John Smith", consent.getName());
        assertEquals("1999-12-01", consent.getBirthdate());
        assertEquals(TestConstants.DUMMY_IMAGE_DATA, consent.getImageData());
        assertEquals("image/gif", consent.getImageMimeType());
        // Wipe out the signature
        userConsentDao.removeConsentSignature(HEALTH_CODE, STUDY_IDENTIFIER);
        consent = (DynamoUserConsent2)userConsentDao.getUserConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertNull(consent.getName());
        assertNull(consent.getBirthdate());
        assertNull(consent.getImageData());
        assertNull(consent.getImageMimeType());
    }

    private DynamoStudyConsent1 createStudyConsent() {
        final DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(STUDY_IDENTIFIER);
        consent.setCreatedOn(123L);
        return consent;
    }
}
