package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;

import com.amazonaws.AmazonServiceException;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

@SuppressWarnings("unchecked")
public class EmailVerificationServiceCallWrapperTest {
    private static final String EMAIL_ADDRESS = "example@example.com";
    private static final String HELLO_WORLD = "Hello world!";

    private Callable<String> mockCallable;
    private EmailVerificationService.AsyncSnsTopicHandler asyncHandler;

    @Before
    public void setup() {
        // Set up service. For testing purposes, set to 2 tries and a large rate limit.
        EmailVerificationService service = new EmailVerificationService();
        service.setMaxSesTries(2);
        service.setSesRateLimit(1000.0);

        // Set up handler.
        asyncHandler = service.new AsyncSnsTopicHandler(EMAIL_ADDRESS);

        // Mock callable.
        mockCallable = mock(Callable.class);
    }

    @Test
    public void immediateSuccess() throws Exception {
        when(mockCallable.call()).thenReturn(HELLO_WORLD);
        String result = asyncHandler.callWrapper(mockCallable);
        assertEquals(HELLO_WORLD, result);
        verify(mockCallable, times(1)).call();
    }

    @Test
    public void internalErrors() throws Exception {
        // Set up callable and its exceptions.
        AmazonServiceException ex1 = makeAwsInternalError("error1");
        AmazonServiceException ex2 = makeAwsInternalError("error2");
        when(mockCallable.call()).thenThrow(ex1, ex2);

        // Execute.
        try {
            asyncHandler.callWrapper(mockCallable);
            fail("expected exception");
        } catch (BridgeServiceException thrownEx) {
            // Make sure the second error is the one that is propagated.
            assertSame(ex2, thrownEx.getCause());
        }

        // We call the callable twice.
        verify(mockCallable, times(2)).call();
    }

    @Test
    public void throttling() throws Exception {
        // Set up callable and its exceptions.
        AmazonServiceException ex1 = makeAwsThrottlingError("error1");
        AmazonServiceException ex2 = makeAwsThrottlingError("error2");
        when(mockCallable.call()).thenThrow(ex1, ex2);

        // Execute.
        try {
            asyncHandler.callWrapper(mockCallable);
            fail("expected exception");
        } catch (BridgeServiceException thrownEx) {
            // Make sure the second error is the one that is propagated.
            assertSame(ex2, thrownEx.getCause());
        }

        // We call the callable twice.
        verify(mockCallable, times(2)).call();
    }

    @Test
    public void nonRetryableError() throws Exception {
        // Set up callable and its exceptions.
        AmazonServiceException awsEx = makeAwsBadRequest("error1");
        when(mockCallable.call()).thenThrow(awsEx);

        // Execute.
        try {
            asyncHandler.callWrapper(mockCallable);
            fail("expected exception");
        } catch (BridgeServiceException thrownEx) {
            // Only 1 error thrown.
            assertSame(awsEx, thrownEx.getCause());
        }

        // We call the callable once.
        verify(mockCallable, times(1)).call();
    }

    @Test
    public void nonAwsError() throws Exception {
        // Set up callable and its exceptions.
        IllegalArgumentException otherEx = new IllegalArgumentException();
        when(mockCallable.call()).thenThrow(otherEx);

        // Execute.
        try {
            asyncHandler.callWrapper(mockCallable);
            fail("expected exception");
        } catch (BridgeServiceException thrownEx) {
            // Only 1 error thrown.
            assertSame(otherEx, thrownEx.getCause());
        }

        // We call the callable once.
        verify(mockCallable, times(1)).call();
    }

    private static AmazonServiceException makeAwsInternalError(String message) {
        AmazonServiceException ex = new AmazonServiceException(message);
        ex.setErrorCode("Internal Error");
        ex.setStatusCode(500);
        return ex;
    }

    private static AmazonServiceException makeAwsThrottlingError(String message) {
        AmazonServiceException ex = new AmazonServiceException(message);
        ex.setErrorCode("Throttling");
        ex.setStatusCode(400);
        return ex;
    }

    private static AmazonServiceException makeAwsBadRequest(String message) {
        AmazonServiceException ex = new AmazonServiceException(message);
        ex.setErrorCode("Bad Request");
        ex.setStatusCode(400);
        return ex;
    }
}