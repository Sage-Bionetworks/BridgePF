package org.sagebionetworks.bridge;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.ENROLLMENT;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
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
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.EmailTemplate.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import play.mvc.Http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class TestUtils {
    
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
                plan.getStrategy().getScheduleForUser(new StudyIdentifierImpl(TEST_STUDY_IDENTIFIER), plan, user));
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
        plan.setStrategy(getStrategy("3", "P3D", "AAA", null));
        plan.setStudyKey(TEST_STUDY_IDENTIFIER);
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("BBB");
        plan.setStrategy(getStrategy("1", "P1D", "BBB", null));
        plan.setStudyKey(TEST_STUDY_IDENTIFIER);
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("CCC");
        plan.setStrategy(getStrategy("2", "P2D", "CCC", TestConstants.TEST_ACTIVITY));
        plan.setStudyKey(TEST_STUDY_IDENTIFIER);
        plans.add(plan);

        return plans;
    }
    
    private static ScheduleStrategy getStrategy(String label, String interval, String guid, Activity activity) {
        if (activity == null) {
            activity = new Activity.Builder().withLabel("Activity " + label).withPublishedSurvey("identifier", guid)
                            .build();
        }
        Schedule schedule = new Schedule();
        schedule.setLabel("Schedule " + label);
        schedule.setInterval(interval);
        schedule.setDelay("P1D");
        schedule.addTimes("13:00");
        schedule.setExpires("PT10H");
        schedule.addActivity(activity);
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        return strategy;
    }
    
    public static DynamoStudy getValidStudy() {
        // This study will save without further modification.
        DynamoStudy study = new DynamoStudy();
        study.setName("Test Study [not API]");
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        study.setVerifyEmailTemplate(new EmailTemplate("subject", "body with ${url}", MimeType.TEXT));
        study.setResetPasswordTemplate(new EmailTemplate("subject", "body with ${url}", MimeType.TEXT));
        study.setIdentifier(TestUtils.randomName());
        study.setMinAgeOfConsent(18);
        study.setMaxNumOfParticipants(200);
        study.setSponsorName("The Council on Test Studies");
        study.setConsentNotificationEmail("bridge-testing+consent@sagebase.org");
        study.setTechnicalEmail("bridge-testing+technical@sagebase.org");
        study.setSupportEmail("bridge-testing+support@sagebase.org");
        study.setUserProfileAttributes(Sets.newHashSet("a", "b"));
        return study;
    }
 }
