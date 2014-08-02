package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoStudyConsentDaoTest {

    @Resource
    private DynamoStudyConsentDao studyConsentDao;

    @Before
    public void before() {
        DynamoTestUtil.clearTable(DynamoStudyConsent.class, "active", "path", "minAge", "version");
    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoStudyConsent.class, "active", "path", "minAge", "version");
    }

    @Test
    public void test() {
        StudyConsent consent1 = studyConsentDao.addConsent("fake-study", "fake-path1", 17);
        assertNotNull(consent1);
        assertFalse(consent1.getActive());
        assertNull(studyConsentDao.getConsent(consent1.getStudyKey()));
        studyConsentDao.setActive(consent1);
        StudyConsent consent = studyConsentDao.getConsent(consent1.getStudyKey());
        assertTrue(consent.getActive());
        assertEquals("fake-study", consent.getStudyKey());
        assertEquals("fake-path1", consent.getPath());
        assertEquals(17, consent.getMinAge());
        assertTrue(consent.getTimestamp() > 0);
        StudyConsent consent2 = studyConsentDao.addConsent("fake-study", "fake-path2", 18);
        studyConsentDao.setActive(consent2);
        consent = studyConsentDao.getConsent(consent.getStudyKey());
        assertTrue(consent.getActive());
        assertEquals("fake-study", consent.getStudyKey());
        assertEquals("fake-path2", consent.getPath());
        assertEquals(18, consent.getMinAge());
        consent = studyConsentDao.getConsent(consent1.getStudyKey(), consent1.getTimestamp());
        assertTrue(consent.getActive());
        assertEquals("fake-study", consent.getStudyKey());
        assertEquals("fake-path1", consent.getPath());
        assertEquals(17, consent.getMinAge());
    }
}
