package org.sagebionetworks.bridge.services.backfill;

import static com.google.common.base.Preconditions.checkNotNull;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.BackfillDao;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stormpath.sdk.account.Account;

public class BackfillRecordFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BackfillDao backfillDao;
    public void setBackfillDao(BackfillDao backfillDao) {
        this.backfillDao = backfillDao;
    }

    /**
     * Creates a new entry and saves it into permanent storage.
     */
    public BackfillRecord createAndSave(BackfillTask task, Study study, Account account, String operation) {
        checkNotNull(task);
        checkNotNull(study);
        checkNotNull(account);
        checkNotNull(operation);
        return backfillDao.createRecord(task.getId(), study.getIdentifier(), account.getEmail(), operation);
    }

    /**
     * Creates a new entry. Note the entry is not persisted anywhere. This is used for reporting the progress
     * to the client only.
     */
    public BackfillRecord createOnly(BackfillTask task, Study study, Account account, final String message) {
        checkNotNull(task);
        checkNotNull(study);
        checkNotNull(account);
        checkNotNull(message);
        final String taskId = task.getId();
        final String studyId = study.getIdentifier();
        final String accountId = account.getEmail();
        return new BackfillRecord() {
            @Override
            public String getTaskId() {
                return taskId;
            }
            @Override
            public long getTimestamp() {
                return DateTime.now(DateTimeZone.UTC).getMillis();
            }
            @Override
            public JsonNode toJsonNode() {
                ObjectNode node = MAPPER.createObjectNode();
                node.put("study", studyId);
                node.put("account", accountId);
                node.put("message", message);
                return node;
            }
        };
    }

    /**
     * Creates a new entry. Note the entry is not persisted anywhere. This is used for reporting the progress
     * to the client only.
     */
    public BackfillRecord createOnly(final BackfillTask task, final String message) {
        checkNotNull(task);
        checkNotNull(message);
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
            public JsonNode toJsonNode() {
                return MAPPER.createObjectNode().textNode(message);
            }
        };
    }
}
