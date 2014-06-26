package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
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

@ContextConfiguration("file:conf/application-context.xml")
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
        HealthId healthId = healthCodeService.create();
        assertNotNull(healthId);
        assertEquals(healthId.getCode(),
                healthCodeService.getHealthCode(healthId.getId()));
    }

    private void clearDynamo() {
        DynamoTestUtil.clearTable(DynamoHealthCode.class, "version");
        DynamoTestUtil.clearTable(DynamoHealthId.class, "code", "version");
    }
}
