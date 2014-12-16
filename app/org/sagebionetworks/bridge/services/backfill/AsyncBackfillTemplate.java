package org.sagebionetworks.bridge.services.backfill;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;

abstract class AsyncBackfillTemplate implements BackfillService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private DistributedLockDao lockDao;

    public void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }

    @Override
    public void backfill(final String user, final String name, final BackfillCallback callback) {
        final Class<? extends AsyncBackfillTemplate> clazz = getClass();
        final String identifier = getClass().getSimpleName();
        String lock = null;
        try {
            lock = lockDao.acquireLock(clazz, identifier, getExpireInSeconds());
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    doBackfill(user, name, callback);
                }
            });
        } catch (ConcurrentModificationException e) {
            // TODO: Query DynamoDB and report back progress
        } finally {
            if (lock != null) {
                lockDao.releaseLock(clazz, identifier, lock);
            }
        }
    }

    /**
     * How long (seconds) should the lock expire.
     */
    abstract int getExpireInSeconds();

    abstract void doBackfill(String user, String name, BackfillCallback callback);
}
