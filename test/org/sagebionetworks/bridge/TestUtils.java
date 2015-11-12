package org.sagebionetworks.bridge;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.Period;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;

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

        Map<String,String[]> headers = Maps.newHashMap();
        headers.put("Content-Type", new String[] {"text/json; charset=UTF-8"});
        Http.Request request = mock(Http.Request.class);
        Http.Response response = mock(Http.Response.class);

        when(request.getHeader(anyString())).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            return headers.get(args[0])[0];
        });
        when(request.headers()).thenReturn(headers);
        when(request.body()).thenReturn(body);

        Http.Context context = mock(Http.Context.class);
        when(context.request()).thenReturn(request);
        when(context.response()).thenReturn(response);

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
    
    public static String randomName(Class<?> clazz) {
        return TestUtils.randomName(clazz.getSimpleName().toLowerCase() + "-");
    }

    public static String randomName(String midFix) {
        return "test-" + midFix + RandomStringUtils.randomAlphabetic(5).toLowerCase();
    }

    public static List<ScheduledActivity> runSchedulerForActivities(List<SchedulePlan> plans, User user, ScheduleContext context) {
        List<ScheduledActivity> scheduledActivities = Lists.newArrayList();
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(context.getStudyIdentifier(), plan, user);
            scheduledActivities.addAll(schedule.getScheduler().getScheduledActivities(plan, context));
        }
        Collections.sort(scheduledActivities, ScheduledActivity.SCHEDULED_ACTIVITY_COMPARATOR);
        return scheduledActivities;
    }
    
    public static List<ScheduledActivity> runSchedulerForActivities(User user, ScheduleContext context) {
        return runSchedulerForActivities(getSchedulePlans(context.getStudyIdentifier()), user, context);
    }
    
    public static List<SchedulePlan> getSchedulePlans(StudyIdentifier studyId) {
        List<SchedulePlan> plans = Lists.newArrayListWithCapacity(3);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("DDD");
        plan.setStrategy(getStrategy("P3D", TestConstants.TEST_1_ACTIVITY));
        plan.setStudyKey(studyId.getIdentifier());
        plan.setMinAppVersion(2);
        plan.setMaxAppVersion(5);
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("BBB");
        plan.setStrategy(getStrategy("P1D", TestConstants.TEST_2_ACTIVITY));
        plan.setStudyKey(studyId.getIdentifier());
        plan.setMinAppVersion(9);
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("CCC");
        plan.setStrategy(getStrategy("P2D", TestConstants.TEST_3_ACTIVITY));
        plan.setStudyKey(studyId.getIdentifier());
        plan.setMinAppVersion(5);
        plan.setMaxAppVersion(8);
        plans.add(plan);

        return plans;
    }
    
    public static SchedulePlan getSimpleSchedulePlan(StudyIdentifier studyId) {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 8 ? * TUE *");
        schedule.addActivity(new Activity.Builder().withLabel("Do task CCC").withTask("CCC").build());
        schedule.setExpires(Period.parse("PT60S"));
        schedule.setLabel("Test label for the user");
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("GGG");
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStudyKey(studyId.getIdentifier());
        plan.setStrategy(strategy);
        return plan;
    }
    
    public static ScheduleStrategy getStrategy(String interval, Activity activity) {
        Schedule schedule = new Schedule();
        schedule.setLabel("Schedule " + activity.getLabel());
        schedule.setInterval(interval);
        schedule.setDelay("P1D");
        schedule.addTimes("13:00");
        schedule.setExpires("PT10H");
        schedule.addActivity(activity);
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        return strategy;
    }
    
    public static DynamoStudy getValidStudy(Class<?> clazz) {
        // This study will save without further modification.
        DynamoStudy study = new DynamoStudy();
        study.setName("Test Study ["+clazz.getSimpleName()+"]");
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        study.setVerifyEmailTemplate(new EmailTemplate("subject", "body with ${url}", MimeType.TEXT));
        study.setResetPasswordTemplate(new EmailTemplate("subject", "body with ${url}", MimeType.TEXT));
        study.setIdentifier(TestUtils.randomName(clazz));
        study.setMinAgeOfConsent(18);
        study.setMaxNumOfParticipants(200);
        study.setSponsorName("The Council on Test Studies");
        study.setConsentNotificationEmail("bridge-testing+consent@sagebase.org");
        study.setTechnicalEmail("bridge-testing+technical@sagebase.org");
        study.setSupportEmail("bridge-testing+support@sagebase.org");
        study.setUserProfileAttributes(Sets.newHashSet("a", "b"));
        study.setTaskIdentifiers(Sets.newHashSet("task1", "task2"));
        study.setDataGroups(Sets.newHashSet("beta_users", "production_users"));
        study.setStrictUploadValidationEnabled(true);
        return study;
    }
    
    public static SchedulePlan getABTestSchedulePlan(StudyIdentifier studyId) {
        Schedule schedule1 = new Schedule();
        schedule1.setScheduleType(ScheduleType.RECURRING);
        schedule1.setCronTrigger("0 0 8 ? * TUE *");
        schedule1.addActivity(new Activity.Builder().withLabel("Do AAA task").withTask("AAA").build());
        schedule1.setExpires(Period.parse("PT1H"));
        schedule1.setLabel("Schedule 1");

        Schedule schedule2 = new Schedule();
        schedule2.setScheduleType(ScheduleType.RECURRING);
        schedule2.setCronTrigger("0 0 8 ? * TUE *");
        schedule2.addActivity(new Activity.Builder().withLabel("Do BBB task").withTask("BBB").build());
        schedule2.setExpires(Period.parse("PT1H"));
        schedule2.setLabel("Schedule 2");

        Schedule schedule3 = new Schedule();
        schedule3.setScheduleType(ScheduleType.RECURRING);
        schedule3.setCronTrigger("0 0 8 ? * TUE *");
        schedule3.addActivity(new Activity.Builder().withLabel("Do CCC task").withTask("CCC").build());
        schedule3.setExpires(Period.parse("PT1H"));
        schedule3.setLabel("Schedule 3");
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("AAA");
        plan.setLabel("Test A/B Schedule");
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStudyKey(studyId.getIdentifier());
        
        ABTestScheduleStrategy strategy = new ABTestScheduleStrategy();
        strategy.addGroup(40, schedule1);
        strategy.addGroup(40, schedule2);
        strategy.addGroup(20, schedule3);
        plan.setStrategy(strategy);
        
        return plan;
    }
    
    /**
     * A convenience for finding a completed survey object for tests.
     * @param makeNew
     * @return
     */
    public static Survey getSurvey(boolean makeNew) {
        return new TestSurvey(makeNew);
    }
    
    public static Set<String> getFieldNamesSet(JsonNode node) {
        HashSet<String> set = new HashSet<>();
        for (Iterator<String> i = node.fieldNames(); i.hasNext(); ) {
            set.add(i.next());
        }
        return set;
    }
    
 }
