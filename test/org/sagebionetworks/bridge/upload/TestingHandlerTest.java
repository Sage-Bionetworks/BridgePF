package org.sagebionetworks.bridge.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;

public class TestingHandlerTest {
    private static final String TEST_FILENAME = "test-filename";
    private static final String TEST_STUDY_ID = "test-study";
    private static final String TEST_UPLOAD_ID = "test-upload";

    @Test
    public void productionHandlerFails() throws Exception {
        // mock objects
        UploadValidationContext mockProdContext = mockContext();

        UploadValidationHandler mockProdHandler = mock(UploadValidationHandler.class);
        doThrow(UploadValidationException.class).when(mockProdHandler).handle(mockProdContext);

        UploadValidationHandler mockTestHandler = mock(UploadValidationHandler.class);
        ContextValidator mockValidator = mock(ContextValidator.class);

        // set up testing handler
        TestingHandler testTestingHandler = new TestingHandler();
        testTestingHandler.setContextValidator(mockValidator);
        testTestingHandler.setProductionHandler(mockProdHandler);
        testTestingHandler.setTestHandler(mockTestHandler);

        // execute
        Exception thrownEx = null;
        try {
            testTestingHandler.handle(mockProdContext);
            fail("expected exception");
        } catch (UploadValidationException ex) {
            thrownEx = ex;
        }
        assertNotNull(thrownEx);

        // validate
        verify(mockProdHandler).handle(mockProdContext);
        verifyZeroInteractions(mockTestHandler, mockValidator);
    }

    @Test
    public void testHandlerFails() throws Exception {
        // mock objects
        UploadValidationContext mockProdContext = mockContext();

        UploadValidationHandler mockTestHandler = mock(UploadValidationHandler.class);
        ArgumentCaptor<UploadValidationContext> testContextCaptor = ArgumentCaptor.forClass(
                UploadValidationContext.class);
        doThrow(UploadValidationException.class).when(mockTestHandler).handle(testContextCaptor.capture());

        UploadValidationHandler mockProdHandler = mock(UploadValidationHandler.class);
        ContextValidator mockValidator = mock(ContextValidator.class);

        // set up testing handler
        TestingHandler testTestingHandler = new TestingHandler();
        testTestingHandler.setContextValidator(mockValidator);
        testTestingHandler.setProductionHandler(mockProdHandler);
        testTestingHandler.setTestHandler(mockTestHandler);

        // execute - Test handler throws, but the exception is squelched because it's not supposed to impact
        // production.
        testTestingHandler.handle(mockProdContext);

        // validate
        verify(mockProdHandler).handle(mockProdContext);
        verifyZeroInteractions(mockValidator);

        UploadValidationContext testContext = testContextCaptor.getValue();
        assertEquals(TEST_STUDY_ID, testContext.getStudy().getIdentifier());
        assertEquals(TEST_UPLOAD_ID, testContext.getUpload().getUploadId());
        assertEquals(TEST_FILENAME, testContext.getUpload().getFilename());
    }

    @Test
    public void validation() throws Exception {
        // mock objects
        UploadValidationContext mockProdContext = mockContext();

        UploadValidationHandler mockProdHandler = mock(UploadValidationHandler.class);
        UploadValidationHandler mockTestHandler = mock(UploadValidationHandler.class);
        ContextValidator mockValidator = mock(ContextValidator.class);

        // set up testing handler
        TestingHandler testTestingHandler = new TestingHandler();
        testTestingHandler.setContextValidator(mockValidator);
        testTestingHandler.setProductionHandler(mockProdHandler);
        testTestingHandler.setTestHandler(mockTestHandler);

        // execute
        testTestingHandler.handle(mockProdContext);

        // validate
        verify(mockProdHandler).handle(mockProdContext);

        ArgumentCaptor<UploadValidationContext> testHandlerArgCaptor = ArgumentCaptor.forClass(
                UploadValidationContext.class);
        verify(mockTestHandler).handle(testHandlerArgCaptor.capture());
        UploadValidationContext testHandlerArg = testHandlerArgCaptor.getValue();
        assertEquals(TEST_STUDY_ID, testHandlerArg.getStudy().getIdentifier());
        assertEquals(TEST_UPLOAD_ID, testHandlerArg.getUpload().getUploadId());
        assertEquals(TEST_FILENAME, testHandlerArg.getUpload().getFilename());

        ArgumentCaptor<UploadValidationContext> validatorTestContextArgCaptor = ArgumentCaptor.forClass(
                UploadValidationContext.class);
        verify(mockValidator).validate(same(mockProdContext), validatorTestContextArgCaptor.capture());
        assertSame(testHandlerArg, validatorTestContextArgCaptor.getValue());
    }

    @Test
    public void validatorThrows() throws Exception {
        // mock objects
        UploadValidationContext mockProdContext = mockContext();

        UploadValidationHandler mockProdHandler = mock(UploadValidationHandler.class);
        UploadValidationHandler mockTestHandler = mock(UploadValidationHandler.class);

        ContextValidator mockValidator = mock(ContextValidator.class);
        ArgumentCaptor<UploadValidationContext> validatorTestContextArgCaptor = ArgumentCaptor.forClass(
                UploadValidationContext.class);
        doThrow(UploadValidationException.class).when(mockValidator).validate(same(mockProdContext),
                validatorTestContextArgCaptor.capture());

        // set up testing handler
        TestingHandler testTestingHandler = new TestingHandler();
        testTestingHandler.setContextValidator(mockValidator);
        testTestingHandler.setProductionHandler(mockProdHandler);
        testTestingHandler.setTestHandler(mockTestHandler);

        // execute
        testTestingHandler.handle(mockProdContext);

        // validate
        verify(mockProdHandler).handle(mockProdContext);

        ArgumentCaptor<UploadValidationContext> testHandlerArgCaptor = ArgumentCaptor.forClass(
                UploadValidationContext.class);
        verify(mockTestHandler).handle(testHandlerArgCaptor.capture());
        UploadValidationContext testHandlerArg = testHandlerArgCaptor.getValue();
        assertEquals(TEST_STUDY_ID, testHandlerArg.getStudy().getIdentifier());
        assertEquals(TEST_UPLOAD_ID, testHandlerArg.getUpload().getUploadId());
        assertEquals(TEST_FILENAME, testHandlerArg.getUpload().getFilename());

        assertSame(testHandlerArg, validatorTestContextArgCaptor.getValue());
    }

    private static UploadValidationContext mockContext() {
        // Testing handler may call context.getStudy().getIdentifier(), context.getUpload().getUploadId(), and
        // context.getUpload().getFilename(), so get those ready

        DynamoStudy study = TestUtils.getValidStudy();
        study.setIdentifier(TEST_STUDY_ID);

        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setFilename(TEST_FILENAME);

        UploadValidationContext mockContext = new UploadValidationContext();
        mockContext.setStudy(study);
        mockContext.setUpload(upload);
        return mockContext;
    }
}
