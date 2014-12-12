package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.json.DateUtils;

public class TestABSchedulePlan extends DynamoSchedulePlan {

    private Schedule schedule1 = new Schedule() {
        {
            setGuid("AAA");
            setScheduleType(ScheduleType.RECURRING);
            setCronTrigger("* * *");
            addActivity(new Activity("Do AAA task", ActivityType.TASK, "task:AAA"));
            setExpires(new Long(60000));
            setLabel("Schedule 1");
        }
    };
    private Schedule schedule2 = new Schedule() {
        {
            setGuid("BBB");
            setScheduleType(ScheduleType.RECURRING);
            setCronTrigger("* * *");
            addActivity(new Activity("Do BBB task", ActivityType.TASK, "task:BBB"));
            setExpires(new Long(60000));
            setLabel("Schedule 2");
        }
    };
    private Schedule schedule3 = new Schedule() {
        {
            setGuid("CCC");
            setScheduleType(ScheduleType.RECURRING);
            setCronTrigger("* * *");
            addActivity(new Activity("Do CCC task", ActivityType.TASK, "task:CCC"));
            setExpires(new Long(60000));
            setLabel("Schedule 3");
        }
    };
    
    public TestABSchedulePlan() {
        setGuid("AAA");
        setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        setStudyKey(TestConstants.TEST_STUDY_IDENTIFIER);
        
        ABTestScheduleStrategy strategy = new ABTestScheduleStrategy();
        strategy.addGroup(40, schedule1);
        strategy.addGroup(40, schedule2);
        strategy.addGroup(20, schedule3);
        setStrategy(strategy);
    }
    
}
