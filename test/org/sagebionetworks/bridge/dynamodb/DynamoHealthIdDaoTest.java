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

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoHealthIdDaoTest {

    @Resource
    private DynamoHealthIdDao healthIdDao;

    @Before
    public void before() {
        DynamoTestUtil.clearTable(DynamoHealthId.class, "code", "version");
    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoHealthId.class, "code", "version");
    }

    @Test
    public void test() {
        assertTrue(healthIdDao.setIfNotExist("123", "789"));
        assertEquals("789", healthIdDao.getCode("123"));
        assertFalse(healthIdDao.setIfNotExist("123", "456"));
        assertNull(healthIdDao.getCode("321"));
    }
}
