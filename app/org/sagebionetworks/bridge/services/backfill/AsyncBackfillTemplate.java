package org.sagebionetworks.bridge.services.backfill;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.BackfillDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.backfill.BackfillStatus;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
abstract class AsyncBackfillTemplate implements BackfillService {

    private final Logger logger = LoggerFactory.getLogger(AsyncBackfillTemplate.class);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private DistributedLockDao lockDao;
    private BackfillDao backfillDao;
    private BackfillRecordFactory backfillRecordFactory;

    @Autowired
    public final void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }
    @Autowired
    public final void setBackfillDao(BackfillDao backfillDao) {
        this.backfillDao = backfillDao;
    }

    public BackfillRecordFactory getBackfillRecordFactory() {
        return backfillRecordFactory;
    }

    @Autowired
    public final void setBackfillRecordFactory(BackfillRecordFactory backfillRecordFactory) {
        this.backfillRecordFactory = backfillRecordFactory;
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
                lock(user, name, callback);
            }
        });
    }

    private void lock(final String user, final String name, final BackfillCallback callback) {
        final Class<? extends AsyncBackfillTemplate> clazz = getClass();
        final String obj = clazz.getSimpleName();
        String lock = null;
        try {
            lock = lockDao.acquireLock(clazz, obj, getLockExpireInSeconds());
            backfillTask(user, name, callback);
        } catch (ConcurrentModificationException e) {
            long since = DateTime.now(DateTimeZone.UTC).getMillis() - getLockExpireInSeconds() * 1000L;
            List<? extends BackfillTask> tasks = backfillDao.getTasks(name, since);
            if (tasks.isEmpty()) {
                throw new RuntimeException("Failed to acquire lock but there is not recent backfill of " + name);
            }
            // A backfill task may complete well within the lock expiration. If we look back over the
            // duration of lock expiration, we could get more than one backfill tasks. Only the most
            // recent one is blocking us; the rest should have finished. Check the most recent one and
            // report back the progress.
            final BackfillTask recentTask = tasks.get(tasks.size() - 1);
            final int count = backfillDao.getRecordCount(recentTask.getId());
            final String msg = "Found a recent task of " + name
                    + " started at " + (new DateTime(recentTask.getTimestamp())).toString()
                    + " with status " + recentTask.getStatus()
                    + " and " + count + " records processed.";
            callback.newRecords(backfillRecordFactory.createOnly(recentTask, msg));
        } finally {
            if (lock != null) {
                lockDao.releaseLock(clazz, obj, lock);
            }
        }
    }

    private void backfillTask(final String user, final String name, final BackfillCallback callback) {
        BackfillTask task = null;
        try {
            task = backfillDao.createTask(name, user);
            callback.start(task);
            backfillDao.updateTaskStatus(task.getId(), BackfillStatus.IN_PROCESS);
            doBackfill(task, callback);
            backfillDao.updateTaskStatus(task.getId(), BackfillStatus.COMPLETED);
        } catch (Throwable t) {
            logger.error("Backfill task " + name + " has failed.", t);
            if (task != null) {
                backfillDao.updateTaskStatus(task.getId(), BackfillStatus.FAILED);
            }
        } finally {
            callback.done();
        }
    }

    /**
     * Records the specified message. This is a convenience method that wraps calling the BackfillRecordFactory to
     * create a message on a task and calls the callback with the BackfillRecord.
     */
    protected void recordMessage(BackfillTask task, BackfillCallback callback, String message) {
        logger.info(message);
        callback.newRecords(backfillRecordFactory.createOnly(task, message));
    }

    /** Similar to recordMessage(), except this logs an error with a stacktrace instead of an info. */
    protected void recordError(BackfillTask task, BackfillCallback callback, String message, Throwable t) {
        logger.error(message, t);
        callback.newRecords(backfillRecordFactory.createOnly(task, message));
    }

    /**
     * How long (in seconds) should the lock expire. This value should be long enough to cover
     * the duration of the entire backfill.
     */
    abstract int getLockExpireInSeconds();

    /**
     * Does the actual backfill for the task. Reports back progress as the backfill goes.
     */
    abstract void doBackfill(BackfillTask task, BackfillCallback callback);
}
