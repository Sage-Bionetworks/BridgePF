package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.validators.SchedulePlanValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CriteriaScheduleStrategyTest {
    
    private static final ClientInfo CLIENT_INFO = ClientInfo.fromUserAgentCache(
            "Cardio Health/1 (Unknown iPhone; iPhone OS/9.0.2) BridgeSDK/4");
    
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
    
    private static final SchedulePlanValidator VALIDATOR = new SchedulePlanValidator(Sets.newHashSet(),
            Sets.newHashSet(TestConstants.TEST_3_ACTIVITY.getTask().getIdentifier()));;
    private static final SchedulePlan PLAN = new DynamoSchedulePlan();
    static {
        PLAN.setLabel("Schedule plan label");
        PLAN.setStudyKey(TEST_STUDY_IDENTIFIER);
    }
    
    private CriteriaScheduleStrategy strategy;
    
    @Before
    public void before() {
        strategy = new CriteriaScheduleStrategy();
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
        JsonNode schCriteria1 = array.get(0);
        assertEquals("ScheduleCriteria", schCriteria1.get("type").asText());
        
        JsonNode criteriaNode = schCriteria1.get("criteria");
        
        JsonNode minVersionsNode = criteriaNode.get("minAppVersions");
        assertEquals(4, minVersionsNode.get(OperatingSystem.IOS).asInt());
        JsonNode maxVersionsNode = criteriaNode.get("maxAppVersions");
        assertEquals(12, maxVersionsNode.get(OperatingSystem.IOS).asInt());
        
        assertNotNull(criteriaNode.get("allOfGroups"));
        assertNotNull(criteriaNode.get("noneOfGroups"));
        assertNotNull(schCriteria1.get("schedule"));
        
        JsonNode schCriteria2 = array.get(1);
        JsonNode criteriaNode2 = schCriteria2.get("criteria");
        Set<String> allOfGroups = arrayToSet(criteriaNode2.get("allOfGroups"));
        assertTrue(allOfGroups.contains("req1"));
        assertTrue(allOfGroups.contains("req2"));
        Set<String> noneOfGroups = arrayToSet(criteriaNode2.get("noneOfGroups"));
        assertTrue(noneOfGroups.contains("proh1"));
        assertTrue(noneOfGroups.contains("proh2"));
        
        // But mostly, if this isn't all serialized, and then deserialized, these won't be equal
        CriteriaScheduleStrategy newStrategy = mapper.readValue(json, CriteriaScheduleStrategy.class);
        assertEquals(strategy, newStrategy);
    }
    
    @Test
    public void filtersOnMinAppVersion() {
        setUpStrategyWithAppVersions();
        setUpStrategyEmptyCriteria();
        
        // First returned because context has no version info
        Schedule schedule = getScheduleFromStrategy(ClientInfo.UNKNOWN_CLIENT);
        assertEquals(SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS, schedule);
        
        // Context version info outside minimum range of first criteria, last one returned
        schedule = getScheduleFromStrategy(CLIENT_INFO);
        assertEquals(SCHEDULE_FOR_STRATEGY_NO_CRITERIA, schedule);
    }
    
    @Test
    public void filtersOnMaxAppVersion() {
        setUpStrategyWithAppVersions();
        setUpStrategyEmptyCriteria();
        
        // First one is returned because client has no version info
        Schedule schedule = getScheduleFromStrategy(ClientInfo.UNKNOWN_CLIENT);
        assertEquals(SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS, schedule);
        
        // Context version info outside maximum range of first criteria, last one returned
        schedule = getScheduleFromStrategy(CLIENT_INFO);
        assertEquals(SCHEDULE_FOR_STRATEGY_NO_CRITERIA, schedule);
    }
    
    @Test
    public void filtersOnRequiredDataGroup() {
        setUpStrategyWithOneRequiredDataGroup();
        setUpStrategyEmptyCriteria();
        
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
        setUpStrategyEmptyCriteria();
        
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
        setUpStrategyEmptyCriteria();
        
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
        setUpStrategyEmptyCriteria();
        
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
        
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier(TestConstants.TEST_STUDY)
                .withClientInfo(CLIENT_INFO)
                .withUserDataGroups(Sets.newHashSet("group1"))
                .withHealthCode("BBB").build();

        Schedule schedule = strategy.getScheduleForUser(PLAN, context);
        assertNull(schedule);
    }
    
    @Test
    public void canMixMultipleCriteria() {
        setUpStrategyWithAppVersions();
        setUpStrategyWithOneRequiredDataGroup();
        setUpStrategyWithProhibitedDataGroups();
        
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier(TestConstants.TEST_STUDY)
                .withClientInfo(CLIENT_INFO)
                .withHealthCode("AAA").build();
        
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
        
        ScheduleContext context = new ScheduleContext.Builder()
                .withUserDataGroups(Sets.newHashSet("req1", "req2"))
                .withStudyIdentifier(TestConstants.TEST_STUDY)
                .withClientInfo(ClientInfo.fromUserAgentCache("app/6")) // in range
                .withHealthCode("AAA").build();
        
        // Matches the first schedule, not the second schedule (although it also matches)
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
        setUpStrategyEmptyCriteria();
        
        Criteria criteria = TestUtils.createCriteria(-2, -10, null, null);
        // We're looking here specifically for errors generated by the strategy, not the plan
        // it's embedded in or the schedules. I've made those valid so they don't add errors.
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_ONE_REQUIRED_DATA_GROUP, criteria));
        
        // Has an invalid schedule
        Schedule schedule = new Schedule();
        strategy.addCriteria(new ScheduleCriteria(schedule, criteria));
        
        try {
            Validate.entityThrowingException(VALIDATOR, PLAN);     
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertError(e, "strategy.scheduleCriteria[1].criteria.allOfGroups", 0, " 'group1' is not in enumeration: <no data groups declared>");
            assertError(e, "strategy.scheduleCriteria[2].criteria.noneOfGroups", 0, " 'group2' is not in enumeration: <no data groups declared>");
            assertError(e, "strategy.scheduleCriteria[2].criteria.noneOfGroups", 1, " 'group1' is not in enumeration: <no data groups declared>");
            assertError(e, "strategy.scheduleCriteria[4].criteria.maxAppVersions.iphone_os", 0, " cannot be less than minAppVersion");
            assertError(e, "strategy.scheduleCriteria[4].criteria.maxAppVersions.iphone_os", 1, " cannot be negative");
            assertError(e, "strategy.scheduleCriteria[4].criteria.minAppVersions.iphone_os", 0, " cannot be negative");
            assertError(e, "strategy.scheduleCriteria[5].criteria.maxAppVersions.iphone_os", 0, " cannot be less than minAppVersion");
            assertError(e, "strategy.scheduleCriteria[5].criteria.maxAppVersions.iphone_os", 1, " cannot be negative");
            assertError(e, "strategy.scheduleCriteria[5].criteria.minAppVersions.iphone_os", 0, " cannot be negative");
            assertError(e, "strategy.scheduleCriteria[5].schedule.activities", 0, " are required");
            assertError(e, "strategy.scheduleCriteria[5].schedule.scheduleType", 0, " is required");
         }
    }
    
    @Test
    public void validScheduleCriteriaPassesValidation() {
        Activity activity = new Activity.Builder().withLabel("Label").withTask(TestConstants.TEST_3_ACTIVITY.getTask())
                .build();
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(activity);

        Criteria criteria = TestUtils.createCriteria(2, 12, null, null);
        
        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(schedule, criteria);
        strategy.addCriteria(scheduleCriteria);

        Validate.entityThrowingException(VALIDATOR, PLAN);
    }
    
    @Test
    public void validateScheduleCriteriaMissing() throws Exception {
        // This doesn't contain the property, it's just deserialized to an empty list
        String json = TestUtils.createJson("{'label':'Schedule plan label','studyKey':'api','strategy':{'type':'CriteriaScheduleStrategy'},'type':'SchedulePlan'}");
        
        SchedulePlan plan = BridgeObjectMapper.get().readValue(json, SchedulePlan.class);
        assertTrue(((CriteriaScheduleStrategy)plan.getStrategy()).getScheduleCriteria().isEmpty());
        
        // Null is safe too
        json = TestUtils.createJson("{'label':'Schedule plan label','studyKey':'api','strategy':{'type':'CriteriaScheduleStrategy','scheduleCriteria':null},'type':'SchedulePlan'}");
        plan = BridgeObjectMapper.get().readValue(json, SchedulePlan.class);
        assertTrue(((CriteriaScheduleStrategy)plan.getStrategy()).getScheduleCriteria().isEmpty());
    }
    
    @Test
    public void validateScheduleCriteriaScheduleMissing() {
        Criteria criteria = TestUtils.createCriteria(2, 12, null, null);
        
        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(null, criteria);
        strategy.addCriteria(scheduleCriteria);

        try {
            Validate.entityThrowingException(VALIDATOR, PLAN);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("strategy.scheduleCriteria[0].schedule is required", e.getErrors().get("strategy.scheduleCriteria[0].schedule").get(0));
        }
    }
    
    @Test
    public void validateScheduleCriteriaCriteriaMissing() {
        Activity activity = new Activity.Builder().withLabel("Label").withTask(TestConstants.TEST_3_ACTIVITY.getTask())
                .build();
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(activity);

        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(schedule, null);
        strategy.addCriteria(scheduleCriteria);

        try {
            Validate.entityThrowingException(VALIDATOR, PLAN);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("strategy.scheduleCriteria[0].criteria is required", e.getErrors().get("strategy.scheduleCriteria[0].criteria").get(0));
        }
    }
    
    private void assertError(InvalidEntityException e, String fieldName, int index, String errorMsg) {
        assertEquals(fieldName+errorMsg, e.getErrors().get(fieldName).get(index));
    }
    
    private Set<String> arrayToSet(JsonNode array) {
        Set<String> set = Sets.newHashSet();
        for (int i=0; i < array.size(); i++) {
            set.add(array.get(i).asText());
        }
        return set;
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
        Criteria criteria = TestUtils.createCriteria(4, 12, null, null);
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS, criteria));
    }

    private void setUpStrategyEmptyCriteria() {
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_NO_CRITERIA, Criteria.create()));
    }

    private void setUpStrategyWithOneRequiredDataGroup() {
        Criteria criteria = TestUtils.createCriteria(null, null, Sets.newHashSet("group1"), null);
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_ONE_REQUIRED_DATA_GROUP, criteria));
    }
    
    private void setUpStrategyWithRequiredDataGroups() {
        Criteria criteria = TestUtils.createCriteria(null, null, Sets.newHashSet("group1", "group2"), null);
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_REQUIRED_DATA_GROUPS, criteria));
    }
    
    private void setUpStrategyWithOneProhibitedDataGroup() {
        Criteria criteria = TestUtils.createCriteria(null, null, null, Sets.newHashSet("group1"));
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_ONE_PROHIBITED_DATA_GROUP, criteria));
    }
    
    private void setUpStrategyWithProhibitedDataGroups() {
        Criteria criteria = TestUtils.createCriteria(null, null, null, Sets.newHashSet("group1", "group2"));
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_PROHIBITED_DATA_GROUPS, criteria));
    }

    private void setUpStrategyWithRequiredAndProhibitedDataGroups() {
        Criteria criteria = TestUtils.createCriteria(null, null, Sets.newHashSet("req1", "req2"), Sets.newHashSet("proh1","proh2"));
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS, criteria));
    }
    
    private void setUpStrategyWithAllRequirements() {
        Criteria criteria = TestUtils.createCriteria(4, 12, Sets.newHashSet("req1", "req2"), Sets.newHashSet("proh1","proh2"));
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_ALL_REQUIREMENTS, criteria));
    }
}
