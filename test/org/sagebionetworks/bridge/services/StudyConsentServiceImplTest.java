package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
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

    @Before
    public void before() {
	helper.createOneUser();
	DynamoTestUtil.clearTable(DynamoStudyConsent1.class, "active", "path", "minAge", "version");
    }

    @After
    public void after() {
	helper.deleteOneUser();
	DynamoTestUtil.clearTable(DynamoStudyConsent1.class, "active", "path", "minAge", "version");
    }

    @Test
    public void test() {
	String studyKey = "study-key";
	String path = "fake-path";
	int minAge = 17;
	StudyConsentForm form = new StudyConsentForm(path, minAge);

	// Adding a consent with a non-admin user fails.
	try {
	    studyConsentService.addConsent(helper.getUser(), studyKey, form);
	    fail("studyConsentService.addConsent allowed a non-admin user to add consent.");
	} catch (BridgeServiceException e) {
	}

	// addConsent should return a non-null consent object.
	StudyConsent addedConsent1 = studyConsentService.addConsent(helper.getAdminUser(), studyKey, form);
	assertNotNull(addedConsent1);

	try {
	    studyConsentService.getActiveConsent(helper.getAdminUser(), studyKey);
	    fail("getActiveConsent should throw exception, as there is no currently active consent.");
	} catch (Exception e) {
	}

	// Get active consent returns the most recently activated consent document.
	StudyConsent activatedConsent = studyConsentService.activateConsent(helper.getAdminUser(), studyKey, addedConsent1.getCreatedOn());
	StudyConsent getActiveConsent = studyConsentService.getActiveConsent(helper.getAdminUser(), studyKey);
	assertTrue(activatedConsent.getCreatedOn() == getActiveConsent.getCreatedOn());

	// Get all consents returns one consent document (addedConsent).
	List<StudyConsent> allConsents = studyConsentService.getAllConsents(helper.getAdminUser(), studyKey);
	assertTrue(allConsents.size() == 1);

	// Cannot delete active consent document.
	try {
	    studyConsentService.deleteConsent(helper.getAdminUser(), studyKey, getActiveConsent.getCreatedOn());
	    fail("Was able to successfully delete active consent, which we should not be able to do.");
	} catch (BridgeServiceException e) {
	}

    }
}
