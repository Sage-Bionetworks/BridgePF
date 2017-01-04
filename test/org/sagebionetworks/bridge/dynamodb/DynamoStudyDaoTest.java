package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.studies.Study;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoStudyDaoTest {
    private static final Logger LOG = LoggerFactory.getLogger(DynamoStudyDaoTest.class);

    private final Set<String> USER_PROFILE_ATTRIBUTES = Sets.newHashSet("can-publish", "can-recontact");
    private final Set<String> TASK_IDENTIFIERS = Sets.newHashSet("task1", "task2");
    private final Set<String> DATA_GROUPS = Sets.newHashSet("beta_users", "production_users");

    private Set<String> studyIdsToDelete;

    @Resource
    DynamoStudyDao studyDao;

    @Before
    public void before() {
        // Clear the set before each test, because JUnit seems to not do this automatically.
        studyIdsToDelete = new HashSet<>();
    }

    @After
    public void after() {
        for (String oneStudyId : studyIdsToDelete) {
            try {
                Study study = studyDao.getStudy(oneStudyId);
                studyDao.deleteStudy(study);
            } catch (RuntimeException ex) {
                LOG.error("Error deleting study " + oneStudyId + ": " + ex.getMessage(), ex);
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

        study = createStudy(study);
        assertNotNull("Study was assigned a version", study.getVersion());
        assertNotNull("Study has an identifier", study.getIdentifier());

        study.setName("This is a test name");
        study = studyDao.updateStudy(study);

        study = studyDao.getStudy(study.getIdentifier());
        assertEquals("Name was set", "This is a test name", study.getName());
        assertEquals("bridge-testing+support@sagebase.org", study.getSupportEmail());
        assertEquals("bridge-testing+consent@sagebase.org", study.getConsentNotificationEmail());
        assertEquals(USER_PROFILE_ATTRIBUTES, study.getUserProfileAttributes());
        assertTrue(study.getUsesCustomExportSchedule());
        assertEquals(TASK_IDENTIFIERS, study.getTaskIdentifiers());
        assertEquals(DATA_GROUPS, study.getDataGroups());

        String identifier = study.getIdentifier();
        studyDao.deleteStudy(study);
        try {
            studyDao.getStudy(identifier);
            fail("Should have thrown EntityNotFoundException");
        } catch (EntityNotFoundException e) {
            // expected
        }
    }

    @Test
    public void deactivateStudy() {
        Study study = TestUtils.getValidStudy(DynamoStudyDaoTest.class);
        createStudy(study);

        studyDao.deactivateStudy(study.getIdentifier());

        // verify if that study still exist in dynamodb
        assertTrue(studyDao.doesIdentifierExist(study.getIdentifier()));
    }
    
    @Test
    public void stringSetsCanBeEmpty() throws Exception {
        Study study = TestUtils.getValidStudy(DynamoStudyDaoTest.class);
        study = createStudy(study);

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
        // create studies
        Study study1 = createStudy(TestUtils.getValidStudy(DynamoStudyDaoTest.class));
        String study1Id = study1.getIdentifier();
        Study study2 = createStudy(TestUtils.getValidStudy(DynamoStudyDaoTest.class));
        String study2Id = study2.getIdentifier();

        // verify that they exist
        {
            List<Study> savedStudies = studyDao.getStudies();
            boolean foundStudy1 = false, foundStudy2 = false;
            for (Study oneStudy : savedStudies) {
                if (study1Id.equals(oneStudy.getIdentifier())) {
                    foundStudy1 = true;
                } else if (study2Id.equals(oneStudy.getIdentifier())) {
                    foundStudy2 = true;
                }
            }
            assertTrue(foundStudy1);
            assertTrue(foundStudy2);
        }

        // delete studies
        studyDao.deleteStudy(study1);
        studyDao.deleteStudy(study2);

        // verify that they don't exist
        {
            List<Study> savedStudies = studyDao.getStudies();
            for (Study oneStudy : savedStudies) {
                if (study1Id.equals(oneStudy.getIdentifier())) {
                    fail("study " + study1Id + " shouldn't exist");
                } else if (study2Id.equals(oneStudy.getIdentifier())) {
                    fail("study " + study2Id + " shouldn't exist");
                }
            }
        }
    }

    @Test
    public void willNotSaveTwoStudiesWithSameIdentifier() {
        Study study;
        try {
            study = TestUtils.getValidStudy(DynamoStudyDaoTest.class);
            study = createStudy(study);
            study.setVersion(null);
            createStudy(study);
            fail("Should have thrown entity exists exception");
        } catch (EntityAlreadyExistsException e) {
            // expected exception
        }
    }

    @Test(expected = EntityAlreadyExistsException.class)
    public void identifierUniquenessEnforcedByVersionChecks() throws Exception {
        Study study = TestUtils.getValidStudy(DynamoStudyDaoTest.class);
        createStudy(study);

        study.setVersion(null); // This is now a "new study"
        createStudy(study);
    }

    @Test(expected = UnauthorizedException.class)
    public void cantDeleteApiStudy() {
        Study apiStudy = studyDao.getStudy("api");
        studyDao.deleteStudy(apiStudy);
    }

    private Study createStudy(Study study) {
        Study createdStudy = studyDao.createStudy(study);
        studyIdsToDelete.add(createdStudy.getIdentifier());
        return createdStudy;
    }
}
