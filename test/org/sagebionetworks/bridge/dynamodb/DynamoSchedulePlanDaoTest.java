package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSchedulePlanDaoTest {

    @Resource
    DynamoSchedulePlanDao schedulePlanDao;
    
    @Test
    public void test() {
        fail("Not yet implemented");
    }

}
