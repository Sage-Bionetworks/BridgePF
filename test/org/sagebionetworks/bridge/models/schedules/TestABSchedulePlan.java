package org.sagebionetworks.bridge.models.schedules;

import org.joda.time.Period;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.json.DateUtils;

public class TestABSchedulePlan extends DynamoSchedulePlan {

    private Schedule schedule1 = new Schedule();
    {
        schedule1.setScheduleType(ScheduleType.RECURRING);
        schedule1.setCronTrigger("0 0 8 ? * TUE *");
        schedule1.addActivity(new Activity("Do AAA task", "task:AAA"));
        schedule1.setExpires(Period.parse("PT1H"));
        schedule1.setLabel("Schedule 1");
    }
    private Schedule schedule2 = new Schedule();
    {
        schedule2.setScheduleType(ScheduleType.RECURRING);
        schedule2.setCronTrigger("0 0 8 ? * TUE *");
        schedule2.addActivity(new Activity("Do BBB task", "task:BBB"));
        schedule2.setExpires(Period.parse("PT1H"));
        schedule2.setLabel("Schedule 2");
    }
    private Schedule schedule3 = new Schedule();
    {
        schedule3.setScheduleType(ScheduleType.RECURRING);
        schedule3.setCronTrigger("0 0 8 ? * TUE *");
        schedule3.addActivity(new Activity("Do CCC task", "task:CCC"));
        schedule3.setExpires(Period.parse("PT1H"));
        schedule3.setLabel("Schedule 3");
    }
    
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
