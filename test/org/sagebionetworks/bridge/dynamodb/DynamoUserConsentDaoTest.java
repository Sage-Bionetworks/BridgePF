package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
        assertFalse(userConsentDao.hasConsented(HEALTH_CODE, consent.getStudyKey()));

        // Give consent
        userConsentDao.giveConsent(HEALTH_CODE, consent);
        assertTrue(userConsentDao.hasConsented(HEALTH_CODE, consent.getStudyKey()));

        // Withdraw
        userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertFalse(userConsentDao.hasConsented(HEALTH_CODE, consent.getStudyKey()));

        // Can give consent again if the previous consent is withdrawn
        userConsentDao.giveConsent(HEALTH_CODE, consent);
        assertTrue(userConsentDao.hasConsented(HEALTH_CODE, consent.getStudyKey()));

        // Withdraw again
        userConsentDao.withdrawConsent(HEALTH_CODE, STUDY_IDENTIFIER);
        assertFalse(userConsentDao.hasConsented(HEALTH_CODE, consent.getStudyKey()));
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

    private DynamoStudyConsent1 createStudyConsent() {
        final DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(STUDY_IDENTIFIER);
        consent.setCreatedOn(123L);
        return consent;
    }
}
