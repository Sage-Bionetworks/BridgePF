package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class HealthDataServiceTest {
    
    @Resource
    private HealthDataServiceImpl healthDataService;
    
    @Resource
    private TestUserAdminHelper helper;
    
    private TestUser testUser;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoHealthDataRecord.class);
        DynamoTestUtil.clearTable(DynamoHealthDataRecord.class);
        testUser = helper.createUser(HealthDataServiceTest.class);
    }
    
    @After
    public void after() {
        helper.deleteUser(testUser);
    }
    
    private HealthDataRecord createHealthDataRecord() {
        Date date = new Date();
        HealthDataRecord record = new DynamoHealthDataRecord();
        record.setStartDate(date.getTime());
        record.setEndDate(date.getTime());
        return record;
    }
    
    private HealthDataKey createKey() {
        Tracker tracker = new Tracker();
        tracker.setIdentifier("1");
        return new HealthDataKey(testUser.getStudy(), tracker, testUser.getUser());
    }

    @Test
    public void crudHealthDataRecord() {
        HealthDataKey key = createKey();
        List<HealthDataRecord> records = Lists.newArrayList(createHealthDataRecord());
        
        records = healthDataService.appendHealthData(key, records);
        assertNotNull("Records were assigned record ids", records.get(0).getGuid());
        
        long datetime = DateUtils.getCurrentMillisFromEpoch()+10000;
        records.get(0).setEndDate(datetime);
        
        healthDataService.updateHealthDataRecord(key, records.get(0));
        
        records = healthDataService.getAllHealthData(key);
        assertEquals("There is one health data record", 1, records.size());
        assertEquals("The end date was updated", datetime, records.get(0).getEndDate());
        
        healthDataService.deleteHealthDataRecord(key, records.get(0).getGuid());
        
        records = healthDataService.getAllHealthData(key);
        assertEquals("There are no health data records after delete", 0, records.size());
        
        for (HealthDataRecord record : records) {
            healthDataService.deleteHealthDataRecord(key, record.getGuid());    
        }
        
    }

}
