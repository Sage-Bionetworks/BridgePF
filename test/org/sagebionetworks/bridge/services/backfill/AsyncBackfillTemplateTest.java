package org.sagebionetworks.bridge.services.backfill;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.dao.BackfillDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillStatus;
import org.sagebionetworks.bridge.models.BackfillTask;

public class AsyncBackfillTemplateTest {

    @Test
    public void test() throws Exception {

        final AsyncBackfillTemplate backfillTemplate = new TestBackfillService();

        // Mock lock
        final Class<TestBackfillService> lockClazz = TestBackfillService.class;
        final String lockObject = TestBackfillService.class.getSimpleName();
        final String lock = "lock";
        final DistributedLockDao lockDao = mock(DistributedLockDao.class);
        when(lockDao.acquireLock(lockClazz, lockObject, TestBackfillService.EXPIRE)).thenReturn(lock);
        backfillTemplate.setDistributedLockDao(lockDao);

        // Mock task and backfill dao
        final String taskName = "taskName";
        final String taskId = "taskId";
        final String user = "user";
        BackfillTask backfillTask = new BackfillTask() {
            @Override
            public String getId() {
                return taskId;
            }
            @Override
            public long getTimestamp() {
                return DateTime.now(DateTimeZone.UTC).getMillis();
            }
            @Override
            public String getName() {
                return taskName;
            }
            @Override
            public String getUser() {
                return user;
            }
            @Override
            public String getStatus() {
                return BackfillStatus.SUBMITTED.name();
            }
        };
        BackfillDao backfillDao = mock(BackfillDao.class);
        when(backfillDao.createTask(taskName, user)).thenReturn(backfillTask);
        backfillTemplate.setBackfillDao(backfillDao);

        // Mock callback
        BackfillCallback callback = mock(BackfillCallback.class);
        backfillTemplate.backfill(user, taskName, callback);
        Thread.sleep(100L);

        // Verify callback
        verify(callback, times(1)).start(backfillTask);
        verify(callback, times(1)).newRecords(any(BackfillRecord.class));
        verify(callback, times(1)).newRecords(any(BackfillRecord.class), any(BackfillRecord.class));
        verify(callback, times(1)).done();

        // Verify lock
        verify(lockDao, times(1)).acquireLock(lockClazz, lockObject, TestBackfillService.EXPIRE);
        verify(lockDao, times(1)).releaseLock(lockClazz, lockObject, lock);

        // Verify backfill dao
        verify(backfillDao, times(1)).createTask(taskName, user);
        verify(backfillDao, times(1)).updateTaskStatus(taskId, BackfillStatus.IN_PROCESS);
        verify(backfillDao, times(1)).updateTaskStatus(taskId, BackfillStatus.COMPLETED);
    }
}
