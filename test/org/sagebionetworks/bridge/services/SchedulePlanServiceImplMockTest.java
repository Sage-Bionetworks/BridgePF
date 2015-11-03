package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.validators.SchedulePlanValidator;

public class SchedulePlanServiceImplMockTest {

    private static StudyIdentifier STUDY_ID = new StudyIdentifierImpl("foo-key");
    
    private SchedulePlanServiceImpl service;
    private SchedulePlanDao schedulePlanDao;
    private SchedulePlanValidator validator;
    private SurveyService surveyService;
    private ScheduledActivityService activityService;
    
    @Before
    public void before() {
        service = new SchedulePlanServiceImpl();
        
        schedulePlanDao = mock(SchedulePlanDao.class);
        validator = new SchedulePlanValidator();
        surveyService = mock(SurveyService.class);
        activityService = mock(ScheduledActivityService.class);
        
        service.setSchedulePlanDao(schedulePlanDao);
        service.setValidator(validator);
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
        service.updateSchedulePlan(plan);
        verify(activityService).deleteActivitiesForSchedulePlan("BBB");
    }
    
    @Test
    public void cleansUpScheduledActivitiesOnDelete() {
        service.deleteSchedulePlan(STUDY_ID, "BBB");
        
        verify(activityService).deleteActivitiesForSchedulePlan("BBB");
    }
}
