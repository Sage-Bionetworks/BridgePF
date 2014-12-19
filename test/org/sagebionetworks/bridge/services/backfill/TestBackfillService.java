package org.sagebionetworks.bridge.services.backfill;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillTask;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TestBackfillService extends AsyncBackfillTemplate {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    int getLockExpireInSeconds() {
        return 60;
    }

    @Override
    void doBackfill(final BackfillTask task, final BackfillCallback callback) {

        callback.newRecords(new BackfillRecord() {
            @Override
            public String getTaskId() {
                return task.getId();
            }
            @Override
            public long getTimestamp() {
                return DateTime.now(DateTimeZone.UTC).getMillis();
            }
            @Override
            public String getRecord() {
                ObjectNode node = MAPPER.createObjectNode();
                node.put("recordId", "1");
                node.put("operation", "created");
                try {
                    return MAPPER.writeValueAsString(node);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        BackfillRecord record2 = new BackfillRecord() {
            @Override
            public String getTaskId() {
                return task.getId();
            }
            @Override
            public long getTimestamp() {
                return DateTime.now(DateTimeZone.UTC).getMillis();
            }
            @Override
            public String getRecord() {
                ObjectNode node = MAPPER.createObjectNode();
                node.put("recordId", "2");
                node.put("operation", "recreted");
                try {
                    return MAPPER.writeValueAsString(node);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        BackfillRecord record3 = new BackfillRecord() {
            @Override
            public String getTaskId() {
                return task.getId();
            }
            @Override
            public long getTimestamp() {
                return DateTime.now(DateTimeZone.UTC).getMillis();
            }
            @Override
            public String getRecord() {
                ObjectNode node = MAPPER.createObjectNode();
                node.put("recordId", "3");
                node.put("operation", "deleted");
                try {
                    return MAPPER.writeValueAsString(node);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        callback.newRecords(record2, record3);
    }
}
