package org.sagebionetworks.bridge;

import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import org.sagebionetworks.bridge.models.schedules.ActivitySchedulerTest;
import org.sagebionetworks.bridge.models.schedules.CronActivitySchedulerTest;
import org.sagebionetworks.bridge.models.schedules.IntervalActivitySchedulerTest;
import org.sagebionetworks.bridge.models.schedules.PersistentActivitySchedulerTest;
import org.sagebionetworks.bridge.services.ScheduledActivityServiceDuplicateTest;
import org.sagebionetworks.bridge.services.ScheduledActivityServiceMockTest;
import org.sagebionetworks.bridge.services.ScheduledActivityServiceOnceTest;
import org.sagebionetworks.bridge.services.ScheduledActivityServiceRecurringTest;

/**
 * These are run as part of the entire test suite, but when working on the scheduling, it is useful
 * to be able to run these tests separately. 
 */
@Ignore
@RunWith(Suite.class)
@Suite.SuiteClasses({
    ScheduledActivityServiceDuplicateTest.class,
    ScheduledActivityServiceMockTest.class,
    ScheduledActivityServiceOnceTest.class,
    ScheduledActivityServiceRecurringTest.class,
    CronActivitySchedulerTest.class,
    IntervalActivitySchedulerTest.class,
    PersistentActivitySchedulerTest.class,
    ActivitySchedulerTest.class
 })
public class SchedulingTestSuite {

}
