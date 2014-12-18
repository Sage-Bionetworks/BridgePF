package org.sagebionetworks.bridge.services.backfill;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.BackfillStatus;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AsyncBackfillTemplate implements BackfillService {

    private final Logger logger = LoggerFactory.getLogger(AsyncBackfillTemplate.class);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private DistributedLockDao lockDao;

    public void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
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
            BackfillTask task = createBackfillTask(user, name);
            callback.start(task);
            doBackfill(task, callback);
        } finally {
            callback.done();
        }
    }

    private BackfillTask createBackfillTask(final String user, final String name) {
        final String taskId = UUID.randomUUID().toString();
        return new BackfillTask() {
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
                return name;
            }
            @Override
            public String getDescription() {
                return name;
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
