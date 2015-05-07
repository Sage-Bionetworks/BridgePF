package org.sagebionetworks.bridge.services.backfill;

import org.sagebionetworks.bridge.models.backfill.BackfillRecord;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;

/**
 * Calls back about the backfill progress.
 */
public interface BackfillCallback {

    /**
     * When a new backfill task starts.
     */
    void start(BackfillTask task);

    /**
     * When new backfill records have been generated.
     * Each backfill record indicates a backfilled unit of data.
     */
    void newRecords(BackfillRecord... records);

    /**
     * When the backfill is completed.
     */
    void done();
}
