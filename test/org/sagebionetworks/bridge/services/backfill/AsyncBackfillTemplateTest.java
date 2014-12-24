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

        AsyncBackfillTemplate backfillTemplate = new TestBackfillService();
        DistributedLockDao lockDao = mock(DistributedLockDao.class);
        when(lockDao.acquireLock(TestBackfillService.class, TestBackfillService.class.getSimpleName()))
                .thenReturn("abc");
        backfillTemplate.setDistributedLockDao(lockDao);

        BackfillTask backfillTask = new BackfillTask() {
            @Override
            public String getId() {
                return "taskId";
            }
            @Override
            public long getTimestamp() {
                return DateTime.now(DateTimeZone.UTC).getMillis();
            }
            @Override
            public String getName() {
                return "test backfill";
            }
            @Override
            public String getUser() {
                return "test user";
            }
            @Override
            public String getStatus() {
                return BackfillStatus.SUBMITTED.name();
            }
        };
        BackfillDao backfillDao = mock(BackfillDao.class);
        when(backfillDao.createTask("test backfill", "test user")).thenReturn(backfillTask);
        backfillTemplate.setBackfillDao(backfillDao);

        BackfillCallback callback = mock(BackfillCallback.class);
        backfillTemplate.backfill("test user", "test backfill", callback);
        Thread.sleep(1000L);
        verify(callback, times(1)).start(any(BackfillTask.class));
        verify(callback, times(1)).newRecords(any(BackfillRecord.class));
        verify(callback, times(1)).newRecords(any(BackfillRecord.class), any(BackfillRecord.class));
        verify(callback, times(1)).done();
    }
}
