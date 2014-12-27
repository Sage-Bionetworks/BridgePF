package org.sagebionetworks.bridge.services.backfill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.models.BackfillRecord;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;

import com.fasterxml.jackson.databind.JsonNode;
import com.stormpath.sdk.account.Account;

public class BackfillRecordFactoryTest {

    @Test
    public void testCreateOnly() {
        BackfillRecordFactory recordFactory = new BackfillRecordFactory();
        BackfillTask task = mock(BackfillTask.class);
        final String taskId = "Task ID";
        when(task.getId()).thenReturn(taskId);
        final String message = "this is a message";
        BackfillRecord record = recordFactory.createOnly(task, message);
        assertEquals(taskId, record.getTaskId());
        assertTrue(record.getTimestamp() <= DateTime.now(DateTimeZone.UTC).getMillis());
        JsonNode node = record.toJsonNode();
        assertNotNull(node);
        assertEquals(message, node.asText());
    }

    @Test
    public void testCreateOnlyWithStudyAccount() {
        BackfillRecordFactory recordFactory = new BackfillRecordFactory();
        BackfillTask task = mock(BackfillTask.class);
        final String taskId = "Task ID";
        when(task.getId()).thenReturn(taskId);
        Study study = mock(Study.class);
        final String studyId = "Study ID";
        when(study.getIdentifier()).thenReturn(studyId);
        Account account = mock(Account.class);
        final String accountId = "Account ID";
        when(account.getEmail()).thenReturn(accountId);
        final String message = "message";
        BackfillRecord record = recordFactory.createOnly(task, study, account, message);
        assertEquals(taskId, record.getTaskId());
        assertTrue(record.getTimestamp() <= DateTime.now(DateTimeZone.UTC).getMillis());
        JsonNode node = record.toJsonNode();
        assertNotNull(node);
        assertEquals(studyId, node.get("study").asText());
        assertEquals(accountId, node.get("account").asText());
        assertEquals(message, node.get("message").asText());
    }
}
