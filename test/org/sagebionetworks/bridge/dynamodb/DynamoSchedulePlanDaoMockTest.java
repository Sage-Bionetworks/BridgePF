package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.schedules.CriteriaScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleCriteria;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class DynamoSchedulePlanDaoMockTest {
    
    private static final Set<String> ALL_OF_GROUPS = Sets.newHashSet("a","b");
    private static final Set<String> NONE_OF_GROUPS = Sets.newHashSet("c","d");
    
    private DynamoSchedulePlanDao dao;
    private DynamoDBMapper mapper;
    private CriteriaDao criteriaDao;
    private DynamoSchedulePlan mockPlan;
    
    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        mapper = mock(DynamoDBMapper.class);
        criteriaDao = mock(CriteriaDao.class);
        dao = new DynamoSchedulePlanDao();
        dao.setSchedulePlanMapper(mapper);
        dao.setCriteriaDao(criteriaDao);
        
        // We need the copy constructor to work in order to verify CriteriaDao works.
        when(criteriaDao.copyCriteria(any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgumentAt(0, String.class);
            Criteria criteria = invocation.getArgumentAt(1, DynamoCriteria.class);
            Criteria actualCriteria = Criteria.create();
            actualCriteria.setKey(key);
            if (criteria != null) {
                actualCriteria.setMinAppVersion(criteria.getMinAppVersion());
                actualCriteria.setMaxAppVersion(criteria.getMaxAppVersion());
                actualCriteria.setAllOfGroups(criteria.getAllOfGroups());
                actualCriteria.setNoneOfGroups(criteria.getNoneOfGroups());
            }
            return actualCriteria;        
        });
        
        Schedule schedule = TestUtils.getSchedule("My Schedule");
        Criteria criteria = Criteria.create();
        criteria.setMinAppVersion(2);
        criteria.setMaxAppVersion(10);
        criteria.setAllOfGroups(ALL_OF_GROUPS);
        criteria.setNoneOfGroups(NONE_OF_GROUPS);
        
        CriteriaScheduleStrategy strategy = new CriteriaScheduleStrategy();
        ScheduleCriteria scheduleCriteria = new ScheduleCriteria.Builder()
                .withSchedule(schedule)
                .withCriteria(criteria).build();
        strategy.getScheduleCriteria().add(scheduleCriteria);
        
        mockPlan = new DynamoSchedulePlan();
        mockPlan.setGuid(BridgeUtils.generateGuid());
        mockPlan.setLabel("Schedule Plan");
        mockPlan.setStudyKey(TEST_STUDY_IDENTIFIER);
        mockPlan.setStrategy(strategy);
        
        List<DynamoSchedulePlan> list = Lists.newArrayList(mockPlan);
        
        QueryResultPage<DynamoSchedulePlan> page = mock(QueryResultPage.class);
        when(page.getResults()).thenReturn(list);
                
        when(mapper.queryPage(eq(DynamoSchedulePlan.class), any())).thenReturn(page);
    }
    
    @Test
    public void getSchedulePlansRetrievesCriteria() {
        List<SchedulePlan> plans = dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        
        SchedulePlan plan = plans.get(0);
        CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        
        Criteria criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertCriteria(criteria);
        
        String key = criteria.getKey();
        verify(criteriaDao).copyCriteria(eq(key), any());
        verify(criteriaDao).getCriteria(key);
        
        // now have criteriaDao return a different criteria object, that should update the plan
        Criteria persistedCriteria = Criteria.create();
        persistedCriteria.setMinAppVersion(1);
        persistedCriteria.setMaxAppVersion(65);
        when(criteriaDao.getCriteria(key)).thenReturn(persistedCriteria);
        
        plans = dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        plan = plans.get(0);
        strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertEquals(new Integer(1), criteria.getMinAppVersion());
        assertEquals(new Integer(65), criteria.getMaxAppVersion());
        assertTrue(criteria.getAllOfGroups().isEmpty());
        assertTrue(criteria.getNoneOfGroups().isEmpty());
    }
    
    @Test
    public void getSchedulePlanRetrievesCriteria() {
        SchedulePlan plan = dao.getSchedulePlan(TEST_STUDY, mockPlan.getGuid());
        CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        
        Criteria criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertCriteria(criteria);
        
        String key = criteria.getKey();
        verify(criteriaDao).copyCriteria(eq(key), any());
        verify(criteriaDao).getCriteria(key);
        
        // now have criteriaDao return a different criteria object, that should update the plan
        Criteria persistedCriteria = Criteria.create();
        persistedCriteria.setMinAppVersion(1);
        persistedCriteria.setMaxAppVersion(65);
        when(criteriaDao.getCriteria(key)).thenReturn(persistedCriteria);
        
        plan = dao.getSchedulePlan(TEST_STUDY, plan.getGuid());
        strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertEquals(new Integer(1), criteria.getMinAppVersion());
        assertEquals(new Integer(65), criteria.getMaxAppVersion());
        assertTrue(criteria.getAllOfGroups().isEmpty());
        assertTrue(criteria.getNoneOfGroups().isEmpty());
    }
    
    @Test
    public void createSchedulePlanWritesCriteria() {
        SchedulePlan plan = dao.createSchedulePlan(TEST_STUDY, mockPlan);
        CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        
        Criteria criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertCriteria(criteria);
        
        ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
        
        verify(criteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        Criteria crit = criteriaCaptor.getValue();
        assertCriteria(crit);        
    }
    
    @Test
    public void updateSchedulePlanWritesCriteria() {
        SchedulePlan plan = dao.updateSchedulePlan(TEST_STUDY, mockPlan);
        CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        
        ScheduleCriteria scheduleCriteria = strategy.getScheduleCriteria().get(0);
        Criteria criteria = scheduleCriteria.getCriteria();
        assertCriteria(criteria);
        
        ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
        
        verify(criteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        Criteria crit = criteriaCaptor.getValue();
        assertCriteria(crit);
        
        Criteria newCriteria = Criteria.create();
        newCriteria.setKey(crit.getKey());
        newCriteria.setMinAppVersion(100);
        newCriteria.setMaxAppVersion(200);
        strategy.getScheduleCriteria().set(0, new ScheduleCriteria.Builder()
                .withSchedule(scheduleCriteria.getSchedule())
                .withCriteria(newCriteria).build());
        
        plan = dao.updateSchedulePlan(TEST_STUDY, plan);
        scheduleCriteria = strategy.getScheduleCriteria().get(0);
        criteria = scheduleCriteria.getCriteria();
        assertEquals(new Integer(100), criteria.getMinAppVersion());
        assertEquals(new Integer(200), criteria.getMaxAppVersion());
        
        verify(criteriaDao).createOrUpdateCriteria(newCriteria);
    }
    
    @Test
    public void deleteSchedulePlanDeletesCriteria() {
        dao.deleteSchedulePlan(TEST_STUDY, mockPlan.getGuid());
        
        verify(criteriaDao).deleteCriteria(mockPlan.getGuid()+":scheduleCriteria:0");
    }

    private void assertCriteria(Criteria criteria) {
        assertEquals(new Integer(2), criteria.getMinAppVersion());
        assertEquals(new Integer(10), criteria.getMaxAppVersion());
        assertEquals(ALL_OF_GROUPS, criteria.getAllOfGroups());
        assertEquals(NONE_OF_GROUPS, criteria.getNoneOfGroups());
    }
}
