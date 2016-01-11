package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.validators.SchedulePlanValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CriteriaScheduleStrategyTest {
    
    private static final Schedule SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS = makeValidSchedule(
            "Strategy With App Versions");
    private static final Schedule SCHEDULE_FOR_STRATEGY_WITH_ONE_REQUIRED_DATA_GROUP = makeValidSchedule(
            "Strategy With One Required Data Group");
    private static final Schedule SCHEDULE_FOR_STRATEGY_WITH_REQUIRED_DATA_GROUPS = makeValidSchedule(
            "Strategy With Required Data Groups");
    private static final Schedule SCHEDULE_FOR_STRATEGY_WITH_ONE_PROHIBITED_DATA_GROUP = makeValidSchedule(
            "Strategy With One Prohibited Data Group");
    private static final Schedule SCHEDULE_FOR_STRATEGY_WITH_PROHIBITED_DATA_GROUPS = makeValidSchedule(
            "Strategy With One Prohibited Data Groups");
    private static final Schedule SCHEDULE_FOR_STRATEGY_NO_CRITERIA = makeValidSchedule("Strategy No Criteria");
    private static final Schedule SCHEDULE_FOR_STRATEGY_WITH_ALL_REQUIREMENTS = makeValidSchedule(
            "Strategy with all requirements");
    
    private static final CriteriaScheduleStrategy strategy = new CriteriaScheduleStrategy();
    private static final SchedulePlanValidator VALIDATOR = new SchedulePlanValidator(Sets.newHashSet(),
            Sets.newHashSet(TestConstants.TEST_3_ACTIVITY.getTask().getIdentifier()));;
    private static final SchedulePlan PLAN = new DynamoSchedulePlan();
    static {
        PLAN.setLabel("Schedule plan label");
        PLAN.setStudyKey(TEST_STUDY_IDENTIFIER);
        PLAN.setStrategy(strategy);
    }
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(CriteriaScheduleStrategy.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        
        setUpStrategyWithAppVersions();
        setUpStrategyWithRequiredAndProhibitedDataGroups();
        
        String json = mapper.writeValueAsString(strategy);
        JsonNode node = mapper.readTree(json);

        assertEquals("CriteriaScheduleStrategy", node.get("type").asText());
        assertNotNull(node.get("scheduleCriteria"));
        
        ArrayNode array = (ArrayNode)node.get("scheduleCriteria");
        JsonNode child1 = array.get(0);
        assertEquals("ScheduleCriteria", child1.get("type").asText());
        assertEquals(4, child1.get("minAppVersion").asInt());
        assertEquals(12, child1.get("maxAppVersion").asInt());
        assertNotNull(child1.get("allOfGroups"));
        assertNotNull(child1.get("noneOfGroups"));
        assertNotNull(child1.get("schedule"));
        
        JsonNode child2 = array.get(1);
        Set<String> allOfGroups = arrayToSet(child2.get("allOfGroups"));
        assertTrue(allOfGroups.contains("req1"));
        assertTrue(allOfGroups.contains("req2"));
        Set<String> noneOfGroups = arrayToSet(child2.get("noneOfGroups"));
        assertTrue(noneOfGroups.contains("proh1"));
        assertTrue(noneOfGroups.contains("proh2"));
        
        // But mostly, if this isn't all serialized, and then deserialized, these won't be equal
        CriteriaScheduleStrategy newStrategy = mapper.readValue(json, CriteriaScheduleStrategy.class);
        assertEquals(strategy, newStrategy);
    }
    
    @Test
    public void filtersOnMinAppVersion() {
        setUpStrategyWithAppVersions();
        setUpStrategyNoCriteria();
        
        // First returned because context has no version info
        Schedule schedule = getScheduleFromStrategy(ClientInfo.UNKNOWN_CLIENT);
        assertEquals(SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS, schedule);
        
        // Context version info outside minimum range of first criteria, last one returned
        schedule = getScheduleFromStrategy(ClientInfo.fromUserAgentCache("app/2"));
        assertEquals(SCHEDULE_FOR_STRATEGY_NO_CRITERIA, schedule);
    }
    
    @Test
    public void filtersOnMaxAppVersion() {
        setUpStrategyWithAppVersions();
        setUpStrategyNoCriteria();
        
        // First one is returned because client has no version info
        Schedule schedule = getScheduleFromStrategy(ClientInfo.UNKNOWN_CLIENT);
        assertEquals(SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS, schedule);
        
        // Context version info outside maximum range of first criteria, last one returned
        schedule = getScheduleFromStrategy(ClientInfo.fromUserAgentCache("app/44"));
        assertEquals(SCHEDULE_FOR_STRATEGY_NO_CRITERIA, schedule);
    }
    
    @Test
    public void filtersOnRequiredDataGroup() {
        setUpStrategyWithOneRequiredDataGroup();
        setUpStrategyNoCriteria();
        
        // context has a group required by first group, it's returned
        Schedule schedule = getScheduleFromStrategy(Sets.newHashSet("group1"));
        assertEquals(SCHEDULE_FOR_STRATEGY_WITH_ONE_REQUIRED_DATA_GROUP, schedule);
        
        // context does not have a required group, last one returned
        schedule = getScheduleFromStrategy(Sets.newHashSet("someRandomToken"));
        assertEquals(SCHEDULE_FOR_STRATEGY_NO_CRITERIA, schedule);
    }
    
    @Test
    public void filtersOnRequiredDataGroups() {
        setUpStrategyWithRequiredDataGroups();
        setUpStrategyNoCriteria();
        
        // context has all the required groups so the first one is returned
        Schedule schedule = getScheduleFromStrategy(Sets.newHashSet("group1","group2","group3"));
        assertEquals(SCHEDULE_FOR_STRATEGY_WITH_REQUIRED_DATA_GROUPS, schedule);
        
        // context does not have *any* the required groups, last one returned
        schedule = getScheduleFromStrategy(Sets.newHashSet("someRandomToken"));
        assertEquals(SCHEDULE_FOR_STRATEGY_NO_CRITERIA, schedule);
        
        // context does not have *all* the required groups, last one returned
        schedule = getScheduleFromStrategy(Sets.newHashSet("group1"));
        assertEquals(SCHEDULE_FOR_STRATEGY_NO_CRITERIA, schedule);
    }
    
    @Test
    public void filtersOnProhibitedDataGroup() {
        setUpStrategyWithOneProhibitedDataGroup();
        setUpStrategyNoCriteria();
        
        // Group not prohibited so first schedule returned
        Schedule schedule = getScheduleFromStrategy(Sets.newHashSet("groupNotProhibited"));
        assertEquals(SCHEDULE_FOR_STRATEGY_WITH_ONE_PROHIBITED_DATA_GROUP, schedule);
        
        // this group is prohibited so second schedule is returned
        schedule = getScheduleFromStrategy(Sets.newHashSet("group1"));
        assertEquals(SCHEDULE_FOR_STRATEGY_NO_CRITERIA, schedule);
    }
    
    @Test
    public void filtersOnProhibitedDataGroups() {
        setUpStrategyWithProhibitedDataGroups();
        setUpStrategyNoCriteria();
        
        // context has a prohibited group, so the last schedule is returned
        Schedule schedule = getScheduleFromStrategy(Sets.newHashSet("group1"));
        assertEquals(SCHEDULE_FOR_STRATEGY_NO_CRITERIA, schedule);
        
        // context has one of the prohibited groups, same thing
        schedule = getScheduleFromStrategy(Sets.newHashSet("foo","group1"));
        assertEquals(SCHEDULE_FOR_STRATEGY_NO_CRITERIA, schedule);
        
        // context has no prohibited groups, first schedule is returned
        schedule = getScheduleFromStrategy(Sets.newHashSet());
        assertEquals(SCHEDULE_FOR_STRATEGY_WITH_PROHIBITED_DATA_GROUPS, schedule);
    }
    
    @Test
    public void noMatchingFilterReturnsNull() {
        setUpStrategyWithAppVersions();
        setUpStrategyWithProhibitedDataGroups();
        
        User user = getUser();
        user.setDataGroups(Sets.newHashSet("group1"));
        ScheduleContext context = new ScheduleContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/44"))
                .withUser(user).build();

        Schedule schedule = strategy.getScheduleForUser(PLAN, context);
        assertNull(schedule);
    }
    
    @Test
    public void canMixMultipleCriteria() {
        setUpStrategyWithAppVersions();
        setUpStrategyWithOneRequiredDataGroup();
        setUpStrategyWithProhibitedDataGroups();
        
        User user = getUser();
        user.setDataGroups(Sets.newHashSet("group3"));
        ScheduleContext context = new ScheduleContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/44"))
                .withUser(user).build();
        
        // First two ScheduleCriteria don't match; the first because the app version is wrong 
        // and the second because the user does not have a required data group. The last ScheduleCriteria 
        // matches and returns the last schedule in the list
        Schedule schedule = strategy.getScheduleForUser(PLAN, context);
        assertEquals(SCHEDULE_FOR_STRATEGY_WITH_PROHIBITED_DATA_GROUPS, schedule);
    }
    
    @Test
    public void willMatchScheduleWithMultipleCriteria() {
        setUpStrategyWithAllRequirements();
        setUpStrategyWithAppVersions(); // certainly should not match this one, although criteria are valid
        
        User user = getUser();
        user.setDataGroups(Sets.newHashSet("req1", "req2")); // both are required
        ScheduleContext context = new ScheduleContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/6")) // in range
                .withUser(user).build();
        
        // First two don't match because app version is wrong and missing required groups
        // The last is returned because the context has no prohibited groups
        Schedule schedule = strategy.getScheduleForUser(PLAN, context);
        assertEquals(SCHEDULE_FOR_STRATEGY_WITH_ALL_REQUIREMENTS, schedule);
    }

    @Test
    public void canGetAllPossibleScheduled() {
        setUpStrategyWithAppVersions();
        setUpStrategyWithOneRequiredDataGroup();
        setUpStrategyWithProhibitedDataGroups();

        List<Schedule> schedules = strategy.getAllPossibleSchedules();
        assertEquals(SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS, schedules.get(0));
        assertEquals(SCHEDULE_FOR_STRATEGY_WITH_ONE_REQUIRED_DATA_GROUP, schedules.get(1));
        assertEquals(SCHEDULE_FOR_STRATEGY_WITH_PROHIBITED_DATA_GROUPS, schedules.get(2));
        assertEquals(3, schedules.size());
    }
    
    @Test
    public void validatesInvalidScheduleCriteria() {
        setUpStrategyWithAppVersions();
        setUpStrategyWithOneRequiredDataGroup();
        setUpStrategyWithProhibitedDataGroups();
        setUpStrategyNoCriteria();
        
        // We're looking here specifically for errors generated by the strategy, not the plan
        // it's embedded in or the schedules. I've made those valid so they don't add errors.
        strategy.addCriteria(new ScheduleCriteria.Builder()
                .withMinAppVersion(-2)
                .withMaxAppVersion(-10)
                .withSchedule(SCHEDULE_FOR_STRATEGY_WITH_ONE_REQUIRED_DATA_GROUP).build());
        
        // Has no schedule
        strategy.addCriteria(new ScheduleCriteria.Builder().build());
        
        // Has an invalid schedule
        Schedule schedule = new Schedule();
        strategy.addCriteria(new ScheduleCriteria.Builder().withSchedule(schedule).build());
        
        try {
            Validate.entityThrowingException(VALIDATOR, PLAN);           
        } catch(InvalidEntityException e) {
            String fieldName = "strategy.scheduleCriteria[1].allOfGroups";
            List<String> errors = e.getErrors().get(fieldName);
            assertEquals(fieldName+" 'group1' is not in enumeration: <no data groups declared>", errors.get(0));
            
            fieldName = "strategy.scheduleCriteria[2].noneOfGroups";
            errors = e.getErrors().get(fieldName);
            assertEquals(fieldName+" 'group2' is not in enumeration: <no data groups declared>", errors.get(0));
            assertEquals(fieldName+" 'group1' is not in enumeration: <no data groups declared>", errors.get(1));
            
            fieldName = "strategy.scheduleCriteria[4].minAppVersion";
            errors = e.getErrors().get(fieldName);
            assertEquals(fieldName+" cannot be negative", errors.get(0));
            
            fieldName = "strategy.scheduleCriteria[4].maxAppVersion";
            errors = e.getErrors().get(fieldName);
            assertEquals(fieldName+" cannot be less than minAppVersion", errors.get(0));
            assertEquals(fieldName+" cannot be negative", errors.get(1));
            
            fieldName = "strategy.scheduleCriteria[5].schedule";
            errors = e.getErrors().get(fieldName);
            assertEquals(fieldName+" is required", errors.get(0));
            
            fieldName = "strategy.scheduleCriteria[6].schedule.scheduleType";
            errors = e.getErrors().get(fieldName);
            assertEquals(fieldName+" is required", errors.get(0));
            
            fieldName = "strategy.scheduleCriteria[6].schedule.activities";
            errors = e.getErrors().get(fieldName);
            assertEquals(fieldName+" are required", errors.get(0));
        }
    }
    
    @Test
    public void validScheduleCriteriaPassesValidation() {
        Activity activity = new Activity.Builder().withLabel("Label").withTask(TestConstants.TEST_3_ACTIVITY.getTask())
                .build();
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(activity);

        ScheduleCriteria criteria = new ScheduleCriteria.Builder()
                .withMinAppVersion(2)
                .withMaxAppVersion(12)
                .withSchedule(schedule).build();
        strategy.addCriteria(criteria);

        Validate.entityThrowingException(VALIDATOR, PLAN);
    }
    
    private Set<String> arrayToSet(JsonNode array) {
        Set<String> set = Sets.newHashSet();
        for (int i=0; i < array.size(); i++) {
            set.add(array.get(i).asText());
        }
        return set;
    }
    
    private User getUser() {
        User user = new User();
        user.setHealthCode("AAA");
        user.setStudyKey(TEST_STUDY_IDENTIFIER);
        return user;
    }
    
    private static Schedule makeValidSchedule(String label) {
        Schedule schedule = new Schedule();
        schedule.setLabel(label);
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(TestConstants.TEST_3_ACTIVITY);
        return schedule;
    }

    private Schedule getScheduleFromStrategy(ClientInfo info) {
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier("test-study")
                .withClientInfo(info).build();
        return strategy.getScheduleForUser(PLAN, context);
    }
    
    private Schedule getScheduleFromStrategy(Set<String> dataGroups) {
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier("test-study")
                .withUserDataGroups(dataGroups).build();
        return strategy.getScheduleForUser(PLAN, context);
    }
    
    private void setUpStrategyWithAppVersions() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .withSchedule(SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS)
            .withMinAppVersion(4)
            .withMaxAppVersion(12).build());
    }

    private void setUpStrategyNoCriteria() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .withSchedule(SCHEDULE_FOR_STRATEGY_NO_CRITERIA).build());
    }

    private void setUpStrategyWithOneRequiredDataGroup() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .addRequiredGroup("group1")
            .withSchedule(SCHEDULE_FOR_STRATEGY_WITH_ONE_REQUIRED_DATA_GROUP).build());
    }
    
    private void setUpStrategyWithRequiredDataGroups() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .addRequiredGroup("group1", "group2")
            .withSchedule(SCHEDULE_FOR_STRATEGY_WITH_REQUIRED_DATA_GROUPS).build());
    }
    
    private void setUpStrategyWithOneProhibitedDataGroup() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .addProhibitedGroup("group1")
            .withSchedule(SCHEDULE_FOR_STRATEGY_WITH_ONE_PROHIBITED_DATA_GROUP).build());
    }
    
    private void setUpStrategyWithProhibitedDataGroups() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .addProhibitedGroup("group1","group2")
            .withSchedule(SCHEDULE_FOR_STRATEGY_WITH_PROHIBITED_DATA_GROUPS).build());
    }

    private void setUpStrategyWithRequiredAndProhibitedDataGroups() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .addRequiredGroup("req1", "req2")
            .addProhibitedGroup("proh1","proh2")
            .withSchedule(SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS).build());
    }
    
    private void setUpStrategyWithAllRequirements() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .withMinAppVersion(4)
            .withMaxAppVersion(12)                
            .addRequiredGroup("req1", "req2")
            .addProhibitedGroup("proh1","proh2")
            .withSchedule(SCHEDULE_FOR_STRATEGY_WITH_ALL_REQUIREMENTS).build());
    }
}
