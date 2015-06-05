package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        List<UploadValidationHandler> handlerList = ImmutableList.<UploadValidationHandler>of(
                new MessageHandler("foo was here"), new MessageHandler("bar was here"),
                new MessageHandler("kilroy was here"));

        // execute
        UploadValidationContext ctx = testHelper(handlerList, UploadStatus.SUCCEEDED);

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

    // helper test method for the exception tests
    private static void testExceptionHelper(Class<? extends Exception> exClass) throws Exception {
        // test handlers
        UploadValidationHandler fooHandler = new MessageHandler("foo succeeded");

        UploadValidationHandler barHandler = mock(UploadValidationHandler.class);
        doThrow(exClass).when(barHandler).handle(notNull(UploadValidationContext.class));

        UploadValidationHandler bazHandler = mock(UploadValidationHandler.class);
        verifyZeroInteractions(bazHandler);

        List<UploadValidationHandler> handlerList = ImmutableList.of(fooHandler, barHandler, bazHandler);

        // execute
        UploadValidationContext ctx = testHelper(handlerList, UploadStatus.VALIDATION_FAILED);

        // Validate validation messages. First message is foo handler. Second message is error message. Just check that
        // the second message exists.
        List<String> messageList = ctx.getMessageList();
        assertEquals(2, messageList.size());
        assertEquals("foo succeeded", messageList.get(0));
        assertFalse(Strings.isNullOrEmpty(messageList.get(1)));
    }

    // helper test method, encapsulating core setup and validation
    private static UploadValidationContext testHelper(List<UploadValidationHandler> handlerList,
            UploadStatus expectedStatus) {
        // input
        DynamoStudy study = TestUtils.getValidStudy();

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
        verify(mockDao).writeValidationStatus(upload2, expectedStatus, ctx.getMessageList());

        return ctx;
    }

    // Test handler that makes its presence known only by writing a message to the validation context.
    private static class MessageHandler implements UploadValidationHandler {
        private final String message;

        public MessageHandler(String message) {
            this.message = message;
        }

        @Override
        public void handle(@Nonnull UploadValidationContext context) throws UploadValidationException {
            context.addMessage(message);
        }
    }
}
