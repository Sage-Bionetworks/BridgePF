package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.json.DateUtils;

public class ScheduleEvaluatorTest {

    private ScheduleEvaluator evaluator;
    private DateTime now;
    private DateTime enrollment;
    
    @Before
    public void before() {
        evaluator = new ScheduleEvaluator();
        now = DateUtils.getCurrentDateTime();
        enrollment = now.plusDays(10);
    }
    
    @Test
    public void oneWeekAfterEnrollmentAt8AM() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING_AFTER_ENROLLMENT);
        schedule.setDelay(Period.parse("P7D"));
        schedule.setCronTrigger("0 0 8 1/1 * ? *");
        
        Task task = evaluator.evaluate(schedule, enrollment);
        
        System.out.println(task.getStartsOn());
        assertNull(task.getEndsOn());
    }
    
}
