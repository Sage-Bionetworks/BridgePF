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
    public void test() {

        // Not consented yet
        final String healthCode = "hc789";
        final DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey("study123");
        consent.setCreatedOn(123L);
        assertFalse(userConsentDao.hasConsented(healthCode, consent));
        assertFalse(userConsentDao.hasConsented2(healthCode, consent));
        assertNull(userConsentDao.getConsentCreatedOn(healthCode, consent.getStudyKey()));

        // Give consent
        final ConsentSignature consentSignature = ConsentSignature.create("John Smith", "1999-12-01",
                TestConstants.DUMMY_IMAGE_DATA, "image/gif");
        userConsentDao.giveConsent(healthCode, consent, consentSignature);
        assertTrue(userConsentDao.hasConsented(healthCode, consent));
        assertTrue(userConsentDao.hasConsented2(healthCode, consent));
        assertEquals(Long.valueOf(123), userConsentDao.getConsentCreatedOn(healthCode, consent.getStudyKey()));
        ConsentSignature cs = userConsentDao.getConsentSignature(healthCode, consent);
        assertEquals(consentSignature.getName(), cs.getName());
        assertEquals(consentSignature.getBirthdate(), cs.getBirthdate());
        assertEquals(consentSignature.getImageData(), cs.getImageData());
        assertEquals(consentSignature.getImageMimeType(), cs.getImageMimeType());

        // Withdraw
        userConsentDao.withdrawConsent(healthCode, consent);
        assertFalse(userConsentDao.hasConsented(healthCode, consent));
        assertFalse(userConsentDao.hasConsented2(healthCode, consent));

        // Can give consent again if the previous consent is withdrawn
        userConsentDao.giveConsent(healthCode, consent, consentSignature);
        assertTrue(userConsentDao.hasConsented(healthCode, consent));
        assertTrue(userConsentDao.hasConsented2(healthCode, consent));

        // Withdraw again
        userConsentDao.withdrawConsent(healthCode, consent);
        assertFalse(userConsentDao.hasConsented(healthCode, consent));
        assertFalse(userConsentDao.hasConsented2(healthCode, consent));

    }

}
