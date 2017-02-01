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
public class DynamoHealthCodeDaoTest {

    @Resource
    private DynamoHealthCodeDao healthCodeDao;

    @Test
    public void test() {
        String healthCode = generateTestGuid();
        String studyId = generateTestGuid();
        String randomString = generateTestGuid();
        
        assertTrue(healthCodeDao.setIfNotExist(healthCode, studyId));
        assertFalse(healthCodeDao.setIfNotExist(healthCode, studyId));
        assertEquals(studyId, healthCodeDao.getStudyIdentifier(healthCode));
        assertNull(healthCodeDao.getStudyIdentifier(randomString));
    }
    
    private String generateTestGuid() {
        return "DynamoHealthIdDaoTest-" + BridgeUtils.generateGuid();
    }
    
}
