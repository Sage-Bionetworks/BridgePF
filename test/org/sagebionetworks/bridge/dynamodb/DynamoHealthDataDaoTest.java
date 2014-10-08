package org.sagebionetworks.bridge.dynamodb;

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
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoHealthDataDaoTest {
    
    @Resource
    private DynamoHealthDataDao healthDataDao;
    
    @Resource
    private TestUserAdminHelper helper;
    
    private UserSession session;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoHealthDataRecord.class);
        DynamoTestUtil.clearTable(DynamoHealthDataRecord.class);
        session = helper.createUser();
    }
    
    @After
    public void after() {
        helper.deleteUser(session);
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
        tracker.setId(1L);
        return new HealthDataKey(helper.getTestStudy(), tracker, session.getUser());
    }

    @Test
    public void crudHealthDataRecord() {
        HealthDataKey key = createKey();
        List<HealthDataRecord> records = Lists.newArrayList(createHealthDataRecord());
        
        records = healthDataDao.appendHealthData(key, records);
        assertNotNull("Records were assigned record ids", records.get(0).getRecordId());
        
        long datetime = DateUtils.getCurrentMillisFromEpoch()+10000;
        records.get(0).setEndDate(datetime);
        
        healthDataDao.updateHealthDataRecord(key, records.get(0));
        
        records = healthDataDao.getAllHealthData(key);
        assertEquals("There is one health data record", 1, records.size());
        assertEquals("The end date was updated", datetime, records.get(0).getEndDate());
        
        healthDataDao.deleteHealthDataRecord(key, records.get(0).getRecordId());
        
        records = healthDataDao.getAllHealthData(key);
        assertEquals("There are no health data records after delete", 0, records.size());
    }

}
