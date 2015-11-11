package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoStudyDaoTest {

    private final Set<String> USER_PROFILE_ATTRIBUTES = Sets.newHashSet("can-publish", "can-recontact");
    private final Set<String> TASK_IDENTIFIERS = Sets.newHashSet("task1", "task2");
    private final Set<String> DATA_GROUPS = Sets.newHashSet("beta_users", "production_users");

    @Resource
    DynamoStudyDao studyDao;

    @BeforeClass
    public static void beforeClass() {
        DynamoInitializer.init(DynamoStudy.class);
    }
    
    @Before
    public void before() throws Exception {
        // We need to leave the test study in the database.
        List<Study> studies = studyDao.getStudies();
        for (Study study : studies) {
            if (!"api".equals(study.getIdentifier())) {
                studyDao.deleteStudy(study);
            }
        }
    }

    @Test
    public void crudOneStudy() {
        Study study = TestUtils.getValidStudy(DynamoStudyDaoTest.class);
        study.setStormpathHref("http://url.com/");
        study.setUserProfileAttributes(USER_PROFILE_ATTRIBUTES);
        study.setTaskIdentifiers(TASK_IDENTIFIERS);
        study.setDataGroups(DATA_GROUPS);

        study = studyDao.createStudy(study);
        assertNotNull("Study was assigned a version", study.getVersion());
        assertNotNull("Study has an identifier", study.getIdentifier());

        study.setName("This is a test name");
        study.setMaxNumOfParticipants(10);
        study = studyDao.updateStudy(study);

        study = studyDao.getStudy(study.getIdentifier());
        assertEquals("Name was set", "This is a test name", study.getName());
        assertEquals("Max participants was set", 10, study.getMaxNumOfParticipants());
        assertEquals("bridge-testing+support@sagebase.org", study.getSupportEmail());
        assertEquals("bridge-testing+consent@sagebase.org", study.getConsentNotificationEmail());
        assertEquals(USER_PROFILE_ATTRIBUTES, study.getUserProfileAttributes());
        assertEquals(TASK_IDENTIFIERS, study.getTaskIdentifiers());
        assertEquals(DATA_GROUPS, study.getDataGroups());

        String identifier = study.getIdentifier();
        studyDao.deleteStudy(study);
        try {
            study = studyDao.getStudy(identifier);
            fail("Should have thrown EntityNotFoundException");
        } catch (EntityNotFoundException e) {
            // expected
        }
    }
    
    @Test
    public void stringSetsCanBeEmpty() throws Exception {
        Study study = TestUtils.getValidStudy(DynamoStudyDaoTest.class);
        study = studyDao.createStudy(study);
        
        // This triggers an error without the JSON serializer annotations because DDB doesn't support empty sets
        study.setTaskIdentifiers(Sets.newHashSet());
        studyDao.updateStudy(study);
        
        // We get what we want here because it deserializes the empty array
        study = studyDao.getStudy(study.getIdentifier()); 
        assertEquals(0, study.getTaskIdentifiers().size());
        
        // These two are now equivalent insofar as they throw no error and the object can always present a non-null field
        study.setTaskIdentifiers(null);
        studyDao.updateStudy(study);
        
        // We get what we want here because we set the field to an empty set in the constructor. It's never null.
        study = studyDao.getStudy(study.getIdentifier());
        assertEquals(0, study.getTaskIdentifiers().size());
    }

    @Test
    public void canRetrieveAllStudies() throws InterruptedException {
        List<Study> studies = Lists.newArrayList();
        try {
            studies.add(studyDao.createStudy(TestUtils.getValidStudy(DynamoStudyDaoTest.class)));
            studies.add(studyDao.createStudy(TestUtils.getValidStudy(DynamoStudyDaoTest.class)));
        
            List<Study> savedStudies = studyDao.getStudies();
            // The five studies, plus the API study we refuse to delete...
            assertEquals("There are three studies", 3, savedStudies.size());
        } finally {
            for (Study study : studies) {
                studyDao.deleteStudy(study);
            }
        }
        List<Study> savedStudies = studyDao.getStudies();
        assertEquals("There should be only one study", 1, savedStudies.size());
        assertEquals("That should be the test study study", "api", savedStudies.get(0).getIdentifier());
    }

    @Test
    public void willNotSaveTwoStudiesWithSameIdentifier() {
        Study study = null;
        Long version = null;
        try {
            study = TestUtils.getValidStudy(DynamoStudyDaoTest.class);
            study = studyDao.createStudy(study);
            version = study.getVersion();
            study.setVersion(null);
            studyDao.createStudy(study);
            fail("Should have thrown entity exists exception");
        } catch (EntityAlreadyExistsException e) {

        } finally {
            study.setVersion(version);
            studyDao.deleteStudy(study);
        }
    }

    @Test(expected = EntityAlreadyExistsException.class)
    public void identifierUniquenessEnforcedByVersionChecks() throws Exception {
        Study study = TestUtils.getValidStudy(DynamoStudyDaoTest.class);
        studyDao.createStudy(study);

        study.setVersion(null); // This is now a "new study"
        studyDao.createStudy(study);
    }

}
