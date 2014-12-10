package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedule;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.json.DateUtils;

public class TestSimpleSchedulePlan extends DynamoSchedulePlan {
    
    private Schedule schedule = new DynamoSchedule() {
        {
            setGuid("DDD");
            setScheduleType(ScheduleType.RECURRING);
            setCronTrigger("* * *");
            addActivity(new Activity(ActivityType.TASK, "task:CCC"));
            setExpires(new Long(60000));
            setLabel("Test label for the user");
        }
    };
    
    public TestSimpleSchedulePlan() {
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        setGuid("GGG");
        setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        setStudyKey(TestConstants.TEST_STUDY_IDENTIFIER);
        setStrategy(strategy);
    }
    
}
