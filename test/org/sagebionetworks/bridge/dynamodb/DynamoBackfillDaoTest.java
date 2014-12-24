package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoBackfillDaoTest {

    @Resource
    private DynamoBackfillDao backfillDao;

    @Before
    public void before() {
        DynamoTestUtil.clearTable(DynamoBackfillTask.class);
        DynamoTestUtil.clearTable(DynamoBackfillRecord.class);
    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoBackfillTask.class);
        DynamoTestUtil.clearTable(DynamoBackfillRecord.class);
    }

    @Test
    public void test() {
        backfillDao.createTask("user", "backfill");
    }
}
