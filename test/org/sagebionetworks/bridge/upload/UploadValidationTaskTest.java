package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import javax.annotation.Nonnull;

import java.util.List;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

public class UploadValidationTaskTest {
    @Test
    public void happyCase() {
        // test handlers
        List<UploadValidationHandler> handlerList = ImmutableList.of(
                new MessageHandler("foo was here"), new MessageHandler("bar was here"),
                new MessageHandler("kilroy was here"), new RecordIdHandler("test-record-id"));

        // execute
        UploadValidationContext ctx = testHelper(handlerList, UploadStatus.SUCCEEDED, "test-record-id");
        assertTrue(ctx.getSuccess());

        // validate that the handlers ran by checking the messages they wrote
        List<String> messageList = ctx.getMessageList();
        assertEquals(3, messageList.size());
        assertEquals("foo was here", messageList.get(0));
        assertEquals("bar was here", messageList.get(1));
        assertEquals("kilroy was here", messageList.get(2));
    }

    @Test
    public void uploadValidationException() throws Exception {
        testExceptionHelper(UploadValidationException.class);
    }

    @Test
    public void runtimeException() throws Exception {
        testExceptionHelper(RuntimeException.class);
    }

    @Test
    public void error() throws Exception {
        testExceptionHelper(OutOfMemoryError.class);
    }

    // helper test method for the exception tests
    private static void testExceptionHelper(Class<? extends Throwable> exClass) throws Exception {
        // test handlers
        UploadValidationHandler fooHandler = new MessageHandler("foo succeeded");

        UploadValidationHandler barHandler = mock(UploadValidationHandler.class);
        doThrow(exClass).when(barHandler).handle(notNull(UploadValidationContext.class));

        UploadValidationHandler bazHandler = mock(UploadValidationHandler.class);
        verifyZeroInteractions(bazHandler);

        UploadValidationHandler recordIdHandler = new RecordIdHandler("never called");

        List<UploadValidationHandler> handlerList = ImmutableList.of(fooHandler, barHandler, bazHandler,
                recordIdHandler);

        // execute
        UploadValidationContext ctx = testHelper(handlerList, UploadStatus.VALIDATION_FAILED, null);
        assertFalse(ctx.getSuccess());

        // Validate validation messages. First message is foo handler. Second message is error message. Just check that
        // the second message exists.
        List<String> messageList = ctx.getMessageList();
        assertEquals(2, messageList.size());
        assertEquals("foo succeeded", messageList.get(0));
        assertFalse(Strings.isNullOrEmpty(messageList.get(1)));
    }

    // helper test method, encapsulating core setup and validation
    private static UploadValidationContext testHelper(List<UploadValidationHandler> handlerList,
            UploadStatus expectedStatus, String expectedRecordId) {
        // input
        DynamoStudy study = TestUtils.getValidStudy(UploadValidationTaskTest.class);

        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setStudy(study);
        ctx.setUpload(upload2);

        // mock dao
        UploadDao mockDao = mock(UploadDao.class);

        // set up validation task
        UploadValidationTask task = new UploadValidationTask(ctx);
        task.setHandlerList(handlerList);
        task.setUploadDao(mockDao);

        // execute
        task.run();

        // validate the upload dao write validation status call
        verify(mockDao).writeValidationStatus(upload2, expectedStatus, ctx.getMessageList(), expectedRecordId);

        return ctx;
    }

    // Test handler that makes its presence known only by writing a message to the validation context.
    private static class MessageHandler implements UploadValidationHandler {
        private final String message;

        public MessageHandler(String message) {
            this.message = message;
        }

        @Override
        public void handle(@Nonnull UploadValidationContext context) {
            context.addMessage(message);
        }
    }

    // Test handler that simulates writing the record ID to the context, so we can test writing the record ID to the
    // validation status.
    private static class RecordIdHandler implements UploadValidationHandler {
        private final String recordId;

        public RecordIdHandler(String recordId) {
            this.recordId = recordId;
        }

        @Override
        public void handle(@Nonnull UploadValidationContext context) {
            context.setRecordId(recordId);
        }
    }
}
