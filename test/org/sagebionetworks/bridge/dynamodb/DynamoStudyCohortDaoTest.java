package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.StudyCohort;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoStudyCohortDaoTest {
    
    StudyIdentifier studyId;
    
    @Resource
    DynamoStudyCohortDao dao;
    
    private Set<StudyCohort> cohortsToDelete;
    
    @BeforeClass
    public static void beforeClass() {
        DynamoInitializer.init(DynamoStudyCohort.class);
    }
    
    @Before
    public void before() {
        studyId = new StudyIdentifierImpl(TestUtils.randomName(DynamoStudyCohortDaoTest.class));
        cohortsToDelete = Sets.newHashSet();
    }
    
    @After
    public void after() {
        for (StudyCohort cohort: cohortsToDelete) {
            dao.deleteStudyCohort(studyId, cohort.getGuid());
        }
        assertTrue(dao.getStudyCohorts(studyId, false).isEmpty());
    }
    
    @Test
    public void crudStudyCohortOK() {
        DynamoStudyCohort cohort = new DynamoStudyCohort();
        cohort.setStudyIdentifier(studyId.getIdentifier());
        cohort.setName("Name");
        cohort.setDescription("Description");
        cohort.setMinAppVersion(2);
        cohort.setMaxAppVersion(10);
        
        // CREATE
        StudyCohort saved = dao.createStudyCohort(cohort);
        assertNotNull(saved.getGuid());
        assertNotNull(saved.getVersion());
        
        // READ
        StudyCohort retrieved = dao.getStudyCohort(studyId, saved.getGuid());
        assertEquals(saved, retrieved);
        
        // UPDATE
        retrieved.setName("Name 2");
        retrieved.setDescription("Description 2");
        StudyCohort finalCohort = dao.updateStudyCohort(retrieved);
        
        // With this change, they should be equivalent using value equality
        retrieved.setVersion(finalCohort.getVersion());
        
        // And delete we handle after every test.
        assertEquals(retrieved, finalCohort);
        
        dao.deleteStudyCohort(studyId, finalCohort.getGuid());
        try {
            dao.getStudyCohort(studyId, finalCohort.getGuid());
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            
        }
    }

    @Test
    public void getStudyCohortsCreatesDefault() {
        List<StudyCohort> cohorts = dao.getStudyCohorts(studyId, true);
        assertEquals(1, cohorts.size());

        StudyCohort cohort = cohorts.get(0);
        assertEquals("Default Cohort", cohort.getName());
        assertEquals(studyId.getIdentifier(), cohort.getGuid());
        assertTrue(cohort.isRequired());
        cohortsToDelete.add(cohort);
    }

    @Test
    public void getStudyCohortForUser() {
        cohort("Cohort 1", 0, 6, "group1"); // match up to version 6 and data group1, specificity 3
        
        cohort("Cohort 2", null, 6, null); // match version 0-6, specificity 2
        
        cohort("Cohort 3", null, null, "group1"); // match group1, specificity 1
        
        cohort("Cohort 4", null, null, null); // match anything, specificity 0
        
        // version 12, no tags == Cohorts 4
        StudyCohort cohort = dao.getStudyCohortForUser(scheduleContext(12, null));
        assertEquals("Cohort 4", cohort.getName());
        
        // version 12, tag group1 == Cohorts 3, 4
        cohort = dao.getStudyCohortForUser(scheduleContext(12, "group1"));
        assertEquals("Cohort 3", cohort.getName());
        
        // version 4, no tag == Cohorts 2, 4
        cohort = dao.getStudyCohortForUser(scheduleContext(4, null));
        assertEquals("Cohort 2", cohort.getName());
        
        // version 4, tag group1 == Cohorts 1,2,3,4, returns 2 in this case (most specific)
        cohort = dao.getStudyCohortForUser(scheduleContext(4, "group1"));
        assertEquals("Cohort 1", cohort.getName());
    }
    
    @Test
    public void deleteStudyCohort() {
    }

    private StudyCohort cohort(String name, Integer min, Integer max, String group) {
        DynamoStudyCohort cohort = new DynamoStudyCohort();
        cohort.setStudyIdentifier(studyId.getIdentifier());
        cohort.setName(name);
        if (min != null) {
            cohort.setMinAppVersion(min);
        }
        if (max != null) {
            cohort.setMaxAppVersion(max);
        }
        if (group != null) {
            cohort.setDataGroup(group);
        }
        dao.createStudyCohort(cohort);
        cohortsToDelete.add(cohort);
        return cohort;
    }
    
    private ScheduleContext scheduleContext(int version, String tag) {
        ScheduleContext.Builder builder = new ScheduleContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/"+version))
                .withStudyIdentifier(studyId.getIdentifier());
        if (tag != null) {
            builder.withUserDataGroups(Sets.newHashSet(tag));    
        }
        return builder.build();
    }
}
