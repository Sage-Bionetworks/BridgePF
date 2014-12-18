package org.sagebionetworks.bridge.services.backfill;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillTask;

public class AsyncBackfillTemplateTest {

    @Test
    public void test() throws Exception {
        AsyncBackfillTemplate backfillTemplate = new TestBackfillService();
        DistributedLockDao lockDao = mock(DistributedLockDao.class);
        when(lockDao.acquireLock(TestBackfillService.class, TestBackfillService.class.getSimpleName()))
                .thenReturn("abc");
        backfillTemplate.setDistributedLockDao(lockDao);
        BackfillCallback callback = mock(BackfillCallback.class);
        backfillTemplate.backfill("test user", "test backfill", callback);
        Thread.sleep(1000L);
        verify(callback, times(1)).start(any(BackfillTask.class));
        verify(callback, times(1)).newRecords(any(BackfillRecord.class));
        verify(callback, times(1)).newRecords(any(BackfillRecord.class), any(BackfillRecord.class));
        verify(callback, times(1)).done();
    }
}
