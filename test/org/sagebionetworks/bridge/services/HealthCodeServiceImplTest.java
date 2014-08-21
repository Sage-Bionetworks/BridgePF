package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthCode;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthId;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.models.HealthId;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class HealthCodeServiceImplTest {

    @Resource
    private HealthCodeService healthCodeService;

    @Before
    public void before() {
        clearDynamo();
    }

    @After
    public void after() {
        clearDynamo();
    }

    @Test
    public void test() {
        HealthId healthId1 = healthCodeService.create();
        assertNotNull(healthId1);
        assertEquals(healthId1.getCode(), healthCodeService.getHealthCode(healthId1.getId()));
        HealthId healthId2 = healthCodeService.create();
        assertFalse(healthId1.getId().equals(healthId2.getId()));
        assertFalse(healthId1.getCode().equals(healthId2.getCode()));
    }

    private void clearDynamo() {
        DynamoTestUtil.clearTable(DynamoHealthCode.class, "version");
        DynamoTestUtil.clearTable(DynamoHealthId.class, "code", "version");
    }
}
