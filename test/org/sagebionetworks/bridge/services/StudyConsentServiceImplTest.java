package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import javax.annotation.Resource;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.StudyConsentForm;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyConsentServiceImplTest {

    @Resource
    private StudyConsentService studyConsentService;

    @Resource
    private TestUserAdminHelper helper;

    @BeforeClass
    public static void initialSetUp() {
        DynamoTestUtil.clearTable(DynamoStudyConsent1.class, "active", "path", "minAge", "version");
    }

    @AfterClass
    public static void finalCleanUp() {
        DynamoTestUtil.clearTable(DynamoStudyConsent1.class, "active", "path", "minAge", "version");
    }

    @Test
    public void test() {

        String studyKey = "study-key";
        String path = "fake-path";
        int minAge = 17;
        StudyConsentForm form = new StudyConsentForm(path, minAge);

        // addConsent should return a non-null consent object.
        StudyConsent addedConsent1 = studyConsentService.addConsent(studyKey, form);
        assertNotNull(addedConsent1);

        try {
            studyConsentService.getActiveConsent(studyKey);
            fail("getActiveConsent should throw exception, as there is no currently active consent.");
        } catch (Exception e) {
        }

        // Get active consent returns the most recently activated consent document.
        StudyConsent activatedConsent = studyConsentService.activateConsent(studyKey, addedConsent1.getCreatedOn());
        StudyConsent getActiveConsent = studyConsentService.getActiveConsent(studyKey);
        assertTrue(activatedConsent.getCreatedOn() == getActiveConsent.getCreatedOn());

        // Get all consents returns one consent document (addedConsent).
        List<StudyConsent> allConsents = studyConsentService.getAllConsents(studyKey);
        assertTrue(allConsents.size() == 1);

        // Cannot delete active consent document.
        try {
            studyConsentService.deleteConsent(studyKey, getActiveConsent.getCreatedOn());
            fail("Was able to successfully delete active consent, which we should not be able to do.");
        } catch (BridgeServiceException e) {
        }
    }
}
