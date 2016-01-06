package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.schedules.CriteriaScheduleStrategy.ScheduleCriteria;
import org.sagebionetworks.bridge.validators.SchedulePlanValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CriteriaScheduleStrategyTest {
    
    private Schedule strategyWithAppVersions = makeValidSchedule("Strategy With App Versions");
    private Schedule strategyWithOneRequiredDataGroup = makeValidSchedule("Strategy With One Required Data Group");
    private Schedule strategyWithRequiredDataGroups = makeValidSchedule("Strategy With Required Data Groups");
    private Schedule strategyWithOneProhibitedDataGroup = makeValidSchedule("Strategy With One Prohibited Data Group");
    private Schedule strategyWithProhibitedDataGroups = makeValidSchedule("Strategy With One Prohibited Data Groups");
    private Schedule strategyNoCriteria = makeValidSchedule("Strategy No Criteria");
    
    private CriteriaScheduleStrategy strategy;
    private SchedulePlan plan;
    private SchedulePlanValidator validator;
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(CriteriaScheduleStrategy.class).allFieldsShouldBeUsed().verify();
    }
    
    @Before
    public void before() {
        strategy = new CriteriaScheduleStrategy();

        plan = new DynamoSchedulePlan();
        plan.setLabel("Schedule plan label");
        plan.setStudyKey(TEST_STUDY_IDENTIFIER);
        plan.setStrategy(strategy);
        
        validator = new SchedulePlanValidator(Sets.newHashSet(),
                Sets.newHashSet(TestConstants.TEST_3_ACTIVITY.getTask().getIdentifier()));
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
        Schedule schedule = getSchedule(ClientInfo.UNKNOWN_CLIENT);
        assertEquals(strategyWithAppVersions, schedule);
        
        // Context version info outside minimum range of first criteria, last one returned
        schedule = getSchedule(ClientInfo.fromUserAgentCache("app/2"));
        assertEquals(strategyNoCriteria, schedule);
    }
    
    @Test
    public void filtersOnMaxAppVersion() {
        setUpStrategyWithAppVersions();
        setUpStrategyNoCriteria();
        
        // First one is returned because client has no version info
        Schedule schedule = getSchedule(ClientInfo.UNKNOWN_CLIENT);
        assertEquals(strategyWithAppVersions, schedule);
        
        // Context version info outside maximum range of first criteria, last one returned
        schedule = getSchedule(ClientInfo.fromUserAgentCache("app/44"));
        assertEquals(strategyNoCriteria, schedule);
    }
    
    @Test
    public void filtersOnRequiredDataGroup() {
        setUpStrategyWithOneRequiredDataGroup();
        setUpStrategyNoCriteria();
        
        // context has a group required by first group, it's returned
        Schedule schedule = getSchedule(Sets.newHashSet("group1"));
        assertEquals(strategyWithOneRequiredDataGroup, schedule);
        
        // context does not have a required group, last one returned
        schedule = getSchedule(Sets.newHashSet("someRandomToken"));
        assertEquals(strategyNoCriteria, schedule);
    }
    
    @Test
    public void filtersOnRequiredDataGroups() {
        setUpStrategyWithRequiredDataGroups();
        setUpStrategyNoCriteria();
        
        // context has all the required groups so the first one is returned
        Schedule schedule = getSchedule(Sets.newHashSet("group1","group2","group3"));
        assertEquals(strategyWithRequiredDataGroups, schedule);
        
        // context does not have *any* the required groups, last one returned
        schedule = getSchedule(Sets.newHashSet("someRandomToken"));
        assertEquals(strategyNoCriteria, schedule);
        
        // context does not have *all* the required groups, last one returned
        schedule = getSchedule(Sets.newHashSet("group1"));
        assertEquals(strategyNoCriteria, schedule);
    }
    
    @Test
    public void filtersOnProhibitedDataGroup() {
        setUpStrategyWithOneProhibitedDataGroup();
        setUpStrategyNoCriteria();
        
        // Group not prohibited so first schedule returned
        Schedule schedule = getSchedule(Sets.newHashSet("groupNotProhibited"));
        assertEquals(strategyWithOneProhibitedDataGroup, schedule);
        
        // this group is prohibited so second schedule is returned
        schedule = getSchedule(Sets.newHashSet("group1"));
        assertEquals(strategyNoCriteria, schedule);
    }
    
    @Test
    public void filtersOnProhibitedDataGroups() {
        setUpStrategyWithProhibitedDataGroups();
        setUpStrategyNoCriteria();
        
        // context has a prohibited group, so the last schedule is returned
        Schedule schedule = getSchedule(Sets.newHashSet("group1"));
        assertEquals(strategyNoCriteria, schedule);
        
        // context has one of the prohibited groups, same thing
        schedule = getSchedule(Sets.newHashSet("foo","group1"));
        assertEquals(strategyNoCriteria, schedule);
        
        // context has no prohibited groups, first schedule is returned
        schedule = getSchedule(Sets.newHashSet());
        assertEquals(strategyWithProhibitedDataGroups, schedule);
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

        Schedule schedule = strategy.getScheduleForUser(plan, context);
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
        
        // First two don't match because app version is wrong and missing required groups
        // The last is returned because the context has no prohibited groups
        Schedule schedule = strategy.getScheduleForUser(plan, context);
        assertEquals(strategyWithProhibitedDataGroups, schedule);
    }

    @Test
    public void canGetAllPossibleScheduled() {
        setUpStrategyWithAppVersions();
        setUpStrategyWithOneRequiredDataGroup();
        setUpStrategyWithProhibitedDataGroups();

        List<Schedule> schedules = strategy.getAllPossibleSchedules();
        assertEquals(3, schedules.size());
    }
    
    @Test
    public void validates() {
        setUpStrategyWithAppVersions();
        setUpStrategyWithOneRequiredDataGroup();
        setUpStrategyWithProhibitedDataGroups();
        setUpStrategyNoCriteria();
        
        // We're looking here specifically for errors generated by the strategy, not the plan
        // it's embedded in or the schedules. I've made those valid so they don't add errors.
        strategy.addCriteria(new ScheduleCriteria.Builder()
                .withMinAppVersion(-2)
                .withMaxAppVersion(-10)
                .withSchedule(strategyWithOneRequiredDataGroup).build());
        
        try {
            Validate.entityThrowingException(validator, plan);            
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
        }
    }
    
    private Set<String> arrayToSet(JsonNode array) {
        Set<String> set = Sets.newHashSet();
        for (int i=0; i < array.size(); i++) {
            set.add(array.get(i).asText());
        }
        return set;
    }
    
    private Schedule makeValidSchedule(String label) {
        Schedule schedule = new Schedule();
        schedule.setLabel(label);
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(TestConstants.TEST_3_ACTIVITY);
        return schedule;
    }

    private Schedule getSchedule(ClientInfo info) {
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier("test-study")
                .withClientInfo(info).build();
        return strategy.getScheduleForUser(plan, context);
    }
    
    private Schedule getSchedule(Set<String> dataGroups) {
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier("test-study")
                .withUserDataGroups(dataGroups).build();
        return strategy.getScheduleForUser(plan, context);
    }
    
    private User getUser() {
        User user = new User();
        user.setHealthCode("AAA");
        user.setStudyKey(TEST_STUDY_IDENTIFIER);
        return user;
    }
    
    private void setUpStrategyWithAppVersions() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .withSchedule(strategyWithAppVersions)
            .withMinAppVersion(4)
            .withMaxAppVersion(12).build());
    }

    private void setUpStrategyNoCriteria() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .withSchedule(strategyNoCriteria).build());
    }

    private void setUpStrategyWithOneRequiredDataGroup() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .addRequiredGroup("group1")
            .withSchedule(strategyWithOneRequiredDataGroup).build());
    }
    
    private void setUpStrategyWithRequiredDataGroups() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .addRequiredGroup("group1", "group2")
            .withSchedule(strategyWithRequiredDataGroups).build());
    }
    
    private void setUpStrategyWithOneProhibitedDataGroup() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .addProhibitedGroup("group1")
            .withSchedule(strategyWithOneProhibitedDataGroup).build());
    }
    
    private void setUpStrategyWithProhibitedDataGroups() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .addProhibitedGroup("group1","group2")
            .withSchedule(strategyWithProhibitedDataGroups).build());
    }

    private void setUpStrategyWithRequiredAndProhibitedDataGroups() {
        strategy.addCriteria(new ScheduleCriteria.Builder()
            .addRequiredGroup("req1", "req2")
            .addProhibitedGroup("proh1","proh2")
            .withSchedule(strategyWithAppVersions).build());
    }
}
