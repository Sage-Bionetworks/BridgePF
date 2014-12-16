package controllers;

import static com.google.common.base.Preconditions.checkNotNull;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.services.backfill.BackfillCallback;

import play.mvc.Results.Chunks;

/**
 * Adapts backfill callback to chunked responses.
 */
class BackfillChunksAdapter implements BackfillCallback {

    private final Chunks.Out<String> chunksOut;

    BackfillChunksAdapter(Chunks.Out<String> out) {
        checkNotNull(out);
        chunksOut = out;
    }

    @Override
    public void start(BackfillTask task) {
        chunksOut.write(task.getName()
                + " started by " + task.getUser()
                + " on " + new DateTime(task.getTimestamp(), DateTimeZone.UTC).toString("YYYY-MM-dd HH:mm:ss"));
    }

    @Override
    public void newRecords(BackfillRecord... records) {
        checkNotNull(records);
        for (BackfillRecord record : records) {
            chunksOut.write(record.getRecord());
        }
    }

    @Override
    public void done() {
        chunksOut.write("Done!");
        chunksOut.close();
    }
}
