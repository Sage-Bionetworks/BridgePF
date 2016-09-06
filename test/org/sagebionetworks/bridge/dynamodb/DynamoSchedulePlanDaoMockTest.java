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
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;

import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

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

@RunWith(MockitoJUnitRunner.class)
public class DynamoSchedulePlanDaoMockTest {
    
    private static final Set<String> ALL_OF_GROUPS = Sets.newHashSet("a","b");
    private static final Set<String> NONE_OF_GROUPS = Sets.newHashSet("c","d");
    
    private DynamoSchedulePlanDao dao;
    
    @Mock
    private DynamoDBMapper mapper;
    
    @Mock
    private CriteriaDao criteriaDao;

    private DynamoSchedulePlan schedulePlan;
    
    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        dao = new DynamoSchedulePlanDao();
        dao.setSchedulePlanMapper(mapper);
        dao.setCriteriaDao(criteriaDao);
        
        schedulePlan = new DynamoSchedulePlan();
        schedulePlan.setGuid(BridgeUtils.generateGuid());
        schedulePlan.setLabel("Schedule Plan");
        schedulePlan.setStudyKey(TEST_STUDY_IDENTIFIER);
        
        Schedule schedule = TestUtils.getSchedule("My Schedule");
        Criteria criteria = TestUtils.createCriteria(2, 10, ALL_OF_GROUPS, NONE_OF_GROUPS);
        criteria.setKey("scheduleCriteria:"+schedulePlan.getGuid()+":0");
        CriteriaScheduleStrategy strategy = new CriteriaScheduleStrategy();
        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(schedule, criteria);
        strategy.getScheduleCriteria().add(scheduleCriteria);
        
        schedulePlan.setStrategy(strategy);
        
        List<DynamoSchedulePlan> list = Lists.newArrayList(schedulePlan);
        
        QueryResultPage<DynamoSchedulePlan> page = mock(QueryResultPage.class);
        when(page.getResults()).thenReturn(list);
                
        when(mapper.queryPage(eq(DynamoSchedulePlan.class), any())).thenReturn(page);
        
        when(criteriaDao.getCriteria("scheduleCriteria:"+schedulePlan.getGuid()+":0")).thenReturn(criteria);
    }
    
    @Test
    public void getSchedulePlansRetrievesCriteria() {
        List<SchedulePlan> plans = dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        
        SchedulePlan plan = plans.get(0);
        CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        
        Criteria criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertCriteria(criteria);
        
        String key = criteria.getKey();
        verify(criteriaDao).getCriteria(key);
        
        // now have criteriaDao return a different criteria object, that should update the plan
        Criteria persistedCriteria = Criteria.create();
        persistedCriteria.setMinAppVersion(IOS, 1);
        persistedCriteria.setMaxAppVersion(IOS, 65);
        when(criteriaDao.getCriteria(key)).thenReturn(persistedCriteria);
        
        plans = dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        plan = plans.get(0);
        strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertEquals(new Integer(1), criteria.getMinAppVersion(IOS));
        assertEquals(new Integer(65), criteria.getMaxAppVersion(IOS));
        assertTrue(criteria.getAllOfGroups().isEmpty());
        assertTrue(criteria.getNoneOfGroups().isEmpty());
    }
    
    @Test
    public void getSchedulePlanRetrievesCriteria() {
        SchedulePlan plan = dao.getSchedulePlan(TEST_STUDY, schedulePlan.getGuid());
        CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        
        Criteria criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertCriteria(criteria);
        
        String key = criteria.getKey();
        verify(criteriaDao).getCriteria(key);
        
        // now have criteriaDao return a different criteria object, that should update the plan
        Criteria persistedCriteria = Criteria.create();
        persistedCriteria.setMinAppVersion(IOS, 1);
        persistedCriteria.setMaxAppVersion(IOS, 65);
        when(criteriaDao.getCriteria(key)).thenReturn(persistedCriteria);
        
        plan = dao.getSchedulePlan(TEST_STUDY, plan.getGuid());
        strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertEquals(new Integer(1), criteria.getMinAppVersion(IOS));
        assertEquals(new Integer(65), criteria.getMaxAppVersion(IOS));
        assertTrue(criteria.getAllOfGroups().isEmpty());
        assertTrue(criteria.getNoneOfGroups().isEmpty());
    }
    
    @Test
    public void createSchedulePlanWritesCriteria() {
        SchedulePlan plan = dao.createSchedulePlan(TEST_STUDY, schedulePlan);
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
        SchedulePlan plan = dao.updateSchedulePlan(TEST_STUDY, schedulePlan);
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
        newCriteria.setMinAppVersion(IOS, 100);
        newCriteria.setMaxAppVersion(IOS, 200);
        strategy.getScheduleCriteria().set(0, new ScheduleCriteria(scheduleCriteria.getSchedule(), newCriteria));
        
        plan = dao.updateSchedulePlan(TEST_STUDY, plan);
        scheduleCriteria = strategy.getScheduleCriteria().get(0);
        criteria = scheduleCriteria.getCriteria();
        assertEquals(new Integer(100), criteria.getMinAppVersion(IOS));
        assertEquals(new Integer(200), criteria.getMaxAppVersion(IOS));
        
        verify(criteriaDao).createOrUpdateCriteria(newCriteria);
    }
    
    @Test
    public void deleteSchedulePlanDeletesCriteria() {
        dao.deleteSchedulePlan(TEST_STUDY, schedulePlan.getGuid());
        
        verify(criteriaDao).deleteCriteria("scheduleCriteria:"+schedulePlan.getGuid()+":0");
    }

    private void assertCriteria(Criteria criteria) {
        assertEquals(new Integer(2), criteria.getMinAppVersion(IOS));
        assertEquals(new Integer(10), criteria.getMaxAppVersion(IOS));
        assertEquals(ALL_OF_GROUPS, criteria.getAllOfGroups());
        assertEquals(NONE_OF_GROUPS, criteria.getNoneOfGroups());
    }
}
