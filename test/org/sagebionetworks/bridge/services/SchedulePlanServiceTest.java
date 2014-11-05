package org.sagebionetworks.bridge.services;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.schedules.TestABSchedulePlan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SchedulePlanServiceTest {

    @Resource
    SchedulePlanServiceImpl schedulePlanService;
    
    // This is extensively tested through the controller, and those tests
    // are now in the SDK project. 
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void cannotCreateExistingSchedulePlan() {
        TestABSchedulePlan plan = new TestABSchedulePlan();
        schedulePlanService.createSchedulePlan(plan);
    }
}
