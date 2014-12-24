package org.sagebionetworks.bridge.services.backfill;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.sagebionetworks.bridge.dao.BackfillDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stormpath.sdk.account.Account;

abstract class AsyncBackfillTemplate implements BackfillService {

    private final Logger logger = LoggerFactory.getLogger(AsyncBackfillTemplate.class);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private DistributedLockDao lockDao;
    public void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }

    private BackfillDao backfillDao;
    public void setBackfillDao(BackfillDao backfillDao) {
        this.backfillDao = backfillDao;
    }

    @Override
    public void backfill(final String user, final String name, final BackfillCallback callback) {
        checkNotNull(user);
        checkNotNull(name);
        checkNotNull(callback);
        async(user, name, callback);
    }

    private void async(final String user, final String name, final BackfillCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                backfillWithLock(user, name, callback);
            }
        });
    }

    private void backfillWithLock(final String user, final String name, final BackfillCallback callback) {
        final Class<? extends AsyncBackfillTemplate> clazz = getClass();
        final String identifier = getClass().getSimpleName();
        String lock = null;
        try {
            lock = lockDao.acquireLock(clazz, identifier, getLockExpireInSeconds());
            backfillTemplate(user, name, callback);
        } catch (ConcurrentModificationException e) {
            // TODO: Query DynamoDB and report back progress
            logger.info("Backfill " + name + " already in process.");
        } finally {
            if (lock != null) {
                lockDao.releaseLock(clazz, identifier, lock);
            }
        }
    }

    private void backfillTemplate(final String user, final String name, final BackfillCallback callback) {
        try {
            BackfillTask task = backfillDao.createTask(name, user);
            callback.start(task);
            doBackfill(task, callback);
        } finally {
            callback.done();
        }
    }

    protected BackfillRecord createRecord(final BackfillTask task, final Study study,
            final Account account, final String operation) {
        return backfillDao.createRecord(task.getId(), study.getIdentifier(), account.getEmail(), operation);
    }

    /**
     * How long (in seconds) should the lock expire.
     */
    abstract int getLockExpireInSeconds();

    /**
     * Does the actual backfill for the task. Reports back progress as backfill goes.
     */
    abstract void doBackfill(BackfillTask task, BackfillCallback callback);
}
