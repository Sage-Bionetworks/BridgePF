package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoStudyConsentDaoTest {

    @Resource
    private DynamoStudyConsentDao studyConsentDao;
 
    private List<StudyConsent> toDelete;

    @Before
    public void before() {
        DynamoInitializer.init(DynamoStudyConsent1.class);
        toDelete = new ArrayList<StudyConsent>();
    }

    @After
    public void after() {
        for (StudyConsent sc : toDelete) {
            studyConsentDao.deleteConsent(sc.getStudyKey(), sc.getCreatedOn());
        }
        toDelete.clear();
    }

    @Test
    public void test() {
        // Add consent version 1, inactive
        final StudyConsent consent1 = studyConsentDao.addConsent("fake-study", "fake-path1", 17);
        assertNotNull(consent1);
        toDelete.add(consent1);
        assertFalse(consent1.getActive());
        assertNull(studyConsentDao.getConsent(consent1.getStudyKey()));
        // Make version1 active
        studyConsentDao.setActive(consent1, true);
        StudyConsent consent = studyConsentDao.getConsent(consent1.getStudyKey());
        assertTrue(consent.getActive());
        assertEquals(consent1.getStudyKey(), consent.getStudyKey());
        assertEquals(consent1.getPath(), consent.getPath());
        assertEquals(consent1.getMinAge(), consent.getMinAge());
        assertTrue(consent.getCreatedOn() > 0);
        // Add version 2
        final StudyConsent consent2 = studyConsentDao.addConsent("fake-study", "fake-path2", 18);
        assertNotNull(consent2);
        toDelete.add(consent2);
        studyConsentDao.setActive(consent1, false);
        studyConsentDao.setActive(consent2, true);
        // The latest should be version 2
        consent = studyConsentDao.getConsent(consent.getStudyKey());
        assertTrue(consent.getActive());
        assertEquals(consent2.getStudyKey(), consent.getStudyKey());
        assertEquals(consent2.getPath(), consent.getPath());
        assertEquals(consent2.getMinAge(), consent.getMinAge());
        // Can still get version 1 using its timestamp
        consent = studyConsentDao.getConsent(consent1.getStudyKey(), consent1.getCreatedOn());
        assertFalse(consent.getActive());
        assertEquals(consent1.getStudyKey(), consent.getStudyKey());
        assertEquals(consent1.getPath(), consent.getPath());
        assertEquals(consent1.getMinAge(), consent.getMinAge());
        // All consents
        final StudyConsent consent3 = studyConsentDao.addConsent("fake-study", "fake-path3", 19);
        assertNotNull(consent3);
        toDelete.add(consent3);
        List<StudyConsent> all = studyConsentDao.getConsents("fake-study");
        assertEquals(3, all.size());
        // In reverse order
        consent = all.get(0);
        assertFalse(consent.getActive());
        assertEquals(consent3.getStudyKey(), consent.getStudyKey());
        assertEquals(consent3.getPath(), consent.getPath());
        assertEquals(consent3.getMinAge(), consent.getMinAge());
        consent = all.get(1);
        assertTrue(consent.getActive());
        assertEquals(consent2.getStudyKey(), consent.getStudyKey());
        assertEquals(consent2.getPath(), consent.getPath());
        assertEquals(consent2.getMinAge(), consent.getMinAge());
        consent = all.get(2);
        assertFalse(consent.getActive());
        assertEquals(consent1.getStudyKey(), consent.getStudyKey());
        assertEquals(consent1.getPath(), consent.getPath());
        assertEquals(consent1.getMinAge(), consent.getMinAge());
        // Delete all consents
        for (StudyConsent x : all) {
            studyConsentDao.deleteConsent(x.getStudyKey(), x.getCreatedOn());
        }
        all = studyConsentDao.getConsents("fake-study");
        assertTrue(all.isEmpty());
    }
}
