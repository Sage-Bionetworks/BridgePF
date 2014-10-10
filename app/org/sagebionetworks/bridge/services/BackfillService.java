package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.Backfill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackfillService {

    private final Logger logger = LoggerFactory.getLogger(BackfillService.class);

    public Backfill backfill(String name) {
        logger.info("Doing backfill " + name);
        Backfill backfill = new Backfill(name);
        return backfill;
    }
}
