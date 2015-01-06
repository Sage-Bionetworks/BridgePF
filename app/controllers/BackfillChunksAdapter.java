package controllers;

import static com.google.common.base.Preconditions.checkNotNull;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.services.backfill.BackfillCallback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import play.mvc.Results.Chunks;

/**
 * Adapts backfill callback to Play chunked responses.
 */
class BackfillChunksAdapter implements BackfillCallback {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Chunks.Out<String> chunksOut;

    BackfillChunksAdapter(Chunks.Out<String> out) {
        checkNotNull(out);
        chunksOut = out;
    }

    @Override
    public void start(BackfillTask task) {
        checkNotNull(task);
        chunksOut.write("<h4>" + task.getName()
                + " started by " + task.getUser()
                + " on " + new DateTime(task.getTimestamp(), DateTimeZone.UTC).toString("YYYY-MM-dd HH:mm:ss")
                + "</h4>");
    }

    @Override
    public void newRecords(BackfillRecord... records) {
        checkNotNull(records);
        for (BackfillRecord record : records) {
            try {
                JsonNode node = record.toJsonNode();
                String prettyPrinted = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
                chunksOut.write("<pre>" + prettyPrinted + "</pre>");
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void done() {
        chunksOut.write("<h4>Done!<h4>");
        chunksOut.close();
    }
}
