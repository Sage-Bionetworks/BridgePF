package org.sagebionetworks.bridge.services.backfill;

public interface BackfillService {

    /**
     * @param user User email or user name of the user who starts the backfill
     * @param name The name of the backfill bean
     * @param callback Callback for backfill record updates
     */
    void backfill(String user, String name, BackfillCallback callback);
}
