package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.google.common.collect.Sets;

public class SchedulePlanServiceImplMockTest {

    private static StudyIdentifier STUDY_ID = new StudyIdentifierImpl("foo-key");
    
    private SchedulePlanServiceImpl service;
    private SchedulePlanDao schedulePlanDao;
    private SurveyService surveyService;
    private ScheduledActivityService activityService;
    private Study study;
    
    @Before
    public void before() {
        study = new DynamoStudy();
        study.setTaskIdentifiers(Sets.newHashSet("CCC"));
        
        service = new SchedulePlanServiceImpl();
        
        schedulePlanDao = mock(SchedulePlanDao.class);
        surveyService = mock(SurveyService.class);
        activityService = mock(ScheduledActivityService.class);
        
        service.setSchedulePlanDao(schedulePlanDao);
        service.setSurveyService(surveyService);
        service.setScheduledActivityService(activityService);
    }
    
    @Test
    public void cleansUpScheduledActivitiesOnUpdate() {
        SchedulePlan plan = TestUtils.getSimpleSchedulePlan(STUDY_ID);
        plan.setLabel("Label");
        plan.setGuid("BBB");
        plan.getStrategy().getAllPossibleSchedules().get(0).setExpires("P3D");
        
        when(schedulePlanDao.updateSchedulePlan(plan)).thenReturn(plan);
        service.updateSchedulePlan(study, plan);
        verify(activityService).deleteActivitiesForSchedulePlan("BBB");
    }
    
    @Test
    public void cleansUpScheduledActivitiesOnDelete() {
        service.deleteSchedulePlan(STUDY_ID, "BBB");
        
        verify(activityService).deleteActivitiesForSchedulePlan("BBB");
    }
}
