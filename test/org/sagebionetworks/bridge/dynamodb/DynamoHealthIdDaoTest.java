package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.BridgeUtils;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoHealthIdDaoTest {

    @Resource
    private DynamoHealthIdDao healthIdDao;

    @Test
    public void test() {
        String healthId = generateTestGuid();
        String healthCode = generateTestGuid();
        String key1 = generateTestGuid();
        String key2 = generateTestGuid();
        
        assertTrue(healthIdDao.setIfNotExist(healthId, healthCode));
        assertEquals(healthCode, healthIdDao.getCode(healthId));
        assertFalse(healthIdDao.setIfNotExist(healthId, key1));
        assertNull(healthIdDao.getCode(key2));
    }
    
    private String generateTestGuid() {
        return "DynamoHealthIdDaoTest-" + BridgeUtils.generateGuid();
    }
}
