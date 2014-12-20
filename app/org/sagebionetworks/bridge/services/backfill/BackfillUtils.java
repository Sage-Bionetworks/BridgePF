package org.sagebionetworks.bridge.services.backfill;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stormpath.sdk.account.Account;

class BackfillUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BackfillUtils() {}

    static BackfillRecord createRecord(final BackfillTask task, final Study study,
            final Account account, final String operation) {

        return new BackfillRecord() {
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
                    node.put("study", study.getIdentifier());
                    node.put("account", account.getEmail());
                    node.put("operation", operation);
                    try {
                        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
    }
}
