package org.sagebionetworks.bridge;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.SchedulerFactory;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.schedules.TaskScheduler;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import play.mvc.Http;

public class TestUtils {

    public static final String ACTIVITY_1 = "task:task3";
    
    public static final String ACTIVITY_2 = "http://webservices.sagebridge.org/api/v1/surveys/AAA/revisions/2015-04-12T14:20:56.123-07:00";
    
    public static final String ACTIVITY_3 = "http://webservices.sagebridge.org/api/v1/surveys/AAA/revisions/published";
    
    private static final DateTime ENROLLMENT = DateTime.parse("2015-04-10T10:40:34.000-07:00");
    
    private static final String STUDY_IDENTIFIER = "api";
    
    public abstract static class FailableRunnable implements Runnable {
        public abstract void testCode() throws Exception;
        @Override
        public void run() {
            try {
                testCode();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Http.Context mockPlayContextWithJson(String json) throws Exception {
        JsonNode node = new ObjectMapper().readTree(json);

        Http.RequestBody body = mock(Http.RequestBody.class);
        when(body.asJson()).thenReturn(node);

        Http.Request request = mock(Http.Request.class);
        when(request.body()).thenReturn(body);

        Http.Context context = mock(Http.Context.class);
        when(context.request()).thenReturn(request);

        return context;
    }

    public static Http.Context mockPlayContext() throws Exception {
        Http.RequestBody body = mock(Http.RequestBody.class);
        when(body.asJson()).thenReturn(null);

        Http.Request request = mock(Http.Request.class);
        when(request.body()).thenReturn(body);

        Http.Context context = mock(Http.Context.class);
        when(context.request()).thenReturn(request);

        return context;
    }
    
    public static String randomName() {
        return "test-" + RandomStringUtils.randomAlphabetic(5).toLowerCase();
    }

    public static List<Task> runSchedulerForTasks(User user, DateTime endsOn) {
        Map<String,DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);

        List<Task> tasks = Lists.newArrayList();
        List<SchedulePlan> plans = getSchedulePlans();
        for (SchedulePlan plan : plans) {
            TaskScheduler scheduler = SchedulerFactory.getScheduler("",
                plan.getStrategy().getScheduleForUser(new StudyIdentifierImpl(STUDY_IDENTIFIER), plan, user));
            for (Task dTask : scheduler.getTasks(events, endsOn)) {
                tasks.add((DynamoTask)dTask);
            }
        }
        return tasks;
    }
    
    public static List<SchedulePlan> getSchedulePlans() {
        List<SchedulePlan> plans = Lists.newArrayListWithCapacity(3);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("DDD");
        plan.setStrategy(getStrategy("3", "P3D", ACTIVITY_1));
        plan.setStudyKey(STUDY_IDENTIFIER);
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("BBB");
        plan.setStrategy(getStrategy("1", "P1D", ACTIVITY_2));
        plan.setStudyKey(STUDY_IDENTIFIER);
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("CCC");
        plan.setStrategy(getStrategy("2", "P2D", ACTIVITY_3));
        plan.setStudyKey(STUDY_IDENTIFIER);
        plans.add(plan);

        return plans;
    }
    
    private static ScheduleStrategy getStrategy(String label, String interval, String activityRef) {
        Schedule schedule = new Schedule();
        schedule.setLabel("Schedule " + label);
        schedule.setInterval(interval);
        schedule.setDelay("P1D");
        schedule.addTimes("13:00");
        schedule.setExpires("PT10H");
        schedule.addActivity(new Activity("Activity " + label, activityRef));
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        return strategy;
    }
    
 }
