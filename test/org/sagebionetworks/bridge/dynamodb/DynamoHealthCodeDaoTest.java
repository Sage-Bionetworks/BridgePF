package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoHealthCodeDaoTest {

    @Resource
    private DynamoHealthCodeDao healthCodeDao;

    @Before
    public void before() {
        DynamoTestUtil.clearTable(DynamoHealthCode.class);
        DynamoTestUtil.clearTable(DynamoHealthCode2.class);
    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoHealthCode.class);
        DynamoTestUtil.clearTable(DynamoHealthCode2.class);
    }

    @Test
    public void test() {
        assertTrue(healthCodeDao.setIfNotExist("123", "789"));
        assertFalse(healthCodeDao.setIfNotExist("123", "789"));
        assertEquals("789", healthCodeDao.getStudyIdentifier("123"));
        assertNull(healthCodeDao.getStudyIdentifier("xyz"));
    }
}
