package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoHealthCode.class);
    }

    @Test
    public void test() {
        assertTrue(healthCodeDao.setIfNotExist("123", "789"));
        assertFalse(healthCodeDao.setIfNotExist("123", "789"));
        assertEquals("789", healthCodeDao.getStudyIdentifier("123"));
        assertNull(healthCodeDao.getStudyIdentifier("xyz"));
    }

    @Test
    public void testSetStudyId() {
        healthCodeDao.setIfNotExist("123");
        assertTrue(healthCodeDao.setStudyId("123", "789"));
        assertFalse(healthCodeDao.setStudyId("123", "789"));
        try {
            healthCodeDao.setStudyId("123", "456");
            fail();
        } catch (RuntimeException e) {
            assertTrue("Exception expected as a different study ID already exists", true);
        }
        try {
            healthCodeDao.setStudyId("xyz", "789");
            fail();
        } catch (RuntimeException e) {
            assertTrue("Exception expected as the health code does not exist", true);
        }
    }
}
