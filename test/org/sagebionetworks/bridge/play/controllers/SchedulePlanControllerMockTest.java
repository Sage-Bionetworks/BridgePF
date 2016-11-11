package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.spy;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.SchedulePlanService;
import org.sagebionetworks.bridge.services.StudyService;

public class SchedulePlanControllerMockTest {

    private SchedulePlanController controller;
    
    @Before
    public void before() {
        controller = spy(new SchedulePlanController());
        
        Study study = new DynamoStudy();
        study.setIdentifier("test-study");
        
        StudyService studyService = mock(StudyService.class);
        when(studyService.getStudy(study.getStudyIdentifier())).thenReturn(study);
        controller.setStudyService(studyService);
        
        SchedulePlanService schedulePlanService = new SchedulePlanService();
        controller.setSchedulePlanService(schedulePlanService);
        
        UserSession session = mock(UserSession.class);
        when(session.getStudyIdentifier()).thenReturn(new StudyIdentifierImpl("test-study"));
        doReturn(session).when(controller).getAuthenticatedSession(any(Roles.class));
    }

    private void assertMessage(InvalidEntityException e, String key, String error) {
        String message = e.getErrors().get(key).get(0);
        assertEquals(key + " " + error, message);
    }
    
    @Test
    public void createSchedulePlanReturnsCompleteErrors() throws Exception {
        // This plan has many problems, and the message should go all the way through 
        // errors even into the second activity and report them back in the exception
        ABTestScheduleStrategy strategy = new ABTestScheduleStrategy();
        Schedule schedule = new Schedule();
        strategy.addGroup(50, schedule);

        schedule = new Schedule();
        schedule.addActivity(new Activity.Builder().withLabel("Foo").build());
        strategy.addGroup(20, schedule);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setStrategy(strategy);

        String json = BridgeObjectMapper.get().writeValueAsString(plan);
        TestUtils.mockPlayContextWithJson(json);
        
        try {
            controller.updateSchedulePlan("test-study");
        } catch(InvalidEntityException e) {
            assertMessage(e, "label", "cannot be missing, null, or blank");
            assertMessage(e, "strategy.scheduleGroups", "groups must add up to 100%");
            assertMessage(e, "strategy.scheduleGroups[0].schedule.activities", "are required");
            assertMessage(e, "strategy.scheduleGroups[0].schedule.scheduleType", "is required");
            assertMessage(e, "strategy.scheduleGroups[1].schedule.scheduleType", "is required");
            assertMessage(e, "strategy.scheduleGroups[1].schedule.activities[0].activity",
                    "must have exactly one of compound activity, task, or survey");
        }
    }
}
