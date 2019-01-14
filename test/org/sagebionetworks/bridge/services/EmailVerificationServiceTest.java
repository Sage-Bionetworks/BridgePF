package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.GetIdentityVerificationAttributesRequest;
import com.amazonaws.services.simpleemail.model.GetIdentityVerificationAttributesResult;
import com.amazonaws.services.simpleemail.model.IdentityVerificationAttributes;
import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
public class EmailVerificationServiceTest {

    private static final String EMAIL_ADDRESS = "foo@foo.com";
    
    private static final CacheKey EMAIL_ADDRESS_KEY = CacheKey.emailVerification(EMAIL_ADDRESS);

    @Mock
    private AmazonSimpleEmailServiceClient sesClient;
    @Mock
    private ExecutorService asyncExecutorService;
    @Mock
    private GetIdentityVerificationAttributesResult result;
    @Mock
    private IdentityVerificationAttributes attributes;
    @Mock
    private CacheProvider cacheProvider;
    @Spy
    private EmailVerificationService service;
    
    private ArgumentCaptor<GetIdentityVerificationAttributesRequest> getCaptor;
    
    @Before
    public void before() {
        service.setAmazonSimpleEmailServiceClient(sesClient);
        service.setAsyncExecutorService(asyncExecutorService);
        service.setCacheProvider(cacheProvider);
    }
    
    private void mockSession(String status) {
        getCaptor = ArgumentCaptor.forClass(GetIdentityVerificationAttributesRequest.class);
        
        Map<String,IdentityVerificationAttributes> map = Maps.newHashMap();
        map.put(EMAIL_ADDRESS, attributes);
        when(result.getVerificationAttributes()).thenReturn(map);
        when(attributes.getVerificationStatus()).thenReturn(status); // aka unverified
        when(sesClient.getIdentityVerificationAttributes(any())).thenReturn(result);
    }
    
    @Test
    public void verifiedEmailTakesNoAction() {
        mockSession("Success");

        EmailVerificationStatus status = service.verifyEmailAddress(EMAIL_ADDRESS);

        assertEquals(EmailVerificationStatus.VERIFIED, status);
        verify(asyncExecutorService, never()).execute(any());
        verify(sesClient).getIdentityVerificationAttributes(getCaptor.capture());
        assertEquals(EMAIL_ADDRESS, getCaptor.getValue().getIdentities().get(0));

        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("VERIFIED"), anyInt());
    }
    
    @Test
    public void ifUnverifiedAttemptsToResendVerification() {
        mockSession("Failure");

        EmailVerificationStatus status = service.verifyEmailAddress(EMAIL_ADDRESS);

        assertEquals(EmailVerificationStatus.PENDING, status);
        verifyAsyncHandler();
        verify(sesClient).getIdentityVerificationAttributes(getCaptor.capture());
        assertEquals(EMAIL_ADDRESS, getCaptor.getValue().getIdentities().get(0));

        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("PENDING"), anyInt());
    }
    
    @Test
    public void emailDoesntExistRequestVerification() {
        mockSession(null);

        EmailVerificationStatus status = service.verifyEmailAddress(EMAIL_ADDRESS);

        assertEquals(EmailVerificationStatus.PENDING, status);
        verifyAsyncHandler();
        verify(sesClient).getIdentityVerificationAttributes(getCaptor.capture());
        assertEquals(EMAIL_ADDRESS, getCaptor.getValue().getIdentities().get(0));
        
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("PENDING"), anyInt());
    }

    @Test
    public void getEmailStatus() {
        mockSession("Success");

        EmailVerificationStatus status = service.getEmailStatus(EMAIL_ADDRESS);

        verify(sesClient).getIdentityVerificationAttributes(any());
        assertEquals(EmailVerificationStatus.VERIFIED, status);
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("VERIFIED"), anyInt());
    }
    
    @Test
    public void getEmailStatusAttributesNull() {
        getCaptor = ArgumentCaptor.forClass(GetIdentityVerificationAttributesRequest.class);

        when(result.getVerificationAttributes()).thenReturn(null);
        when(sesClient.getIdentityVerificationAttributes(any())).thenReturn(result);

        EmailVerificationStatus status = service.getEmailStatus(EMAIL_ADDRESS);

        verify(sesClient).getIdentityVerificationAttributes(any());
        assertEquals(EmailVerificationStatus.UNVERIFIED, status);
        
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("UNVERIFIED"), anyInt());        
    }
    
    @Test
    public void getEmailStatusVerificationStatusNull() {
        getCaptor = ArgumentCaptor.forClass(GetIdentityVerificationAttributesRequest.class);

        Map<String, IdentityVerificationAttributes> map = Maps.newHashMap();
        map.put(EMAIL_ADDRESS, attributes);
        when(result.getVerificationAttributes()).thenReturn(map);
        when(attributes.getVerificationStatus()).thenReturn(null);
        when(sesClient.getIdentityVerificationAttributes(any())).thenReturn(result);

        EmailVerificationStatus status = service.getEmailStatus(EMAIL_ADDRESS);

        verify(sesClient).getIdentityVerificationAttributes(any());
        assertEquals(EmailVerificationStatus.UNVERIFIED, status);
        
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("UNVERIFIED"), anyInt());        
    }

    @Test
    public void isVerifiedAndCached() throws Exception {
        when(cacheProvider.getObject(EMAIL_ADDRESS_KEY, String.class)).thenReturn("VERIFIED");
        assertTrue(service.isVerified(EMAIL_ADDRESS));
    }
    
    @Test
    public void isPendingAndCached() throws Exception {
        when(cacheProvider.getObject(EMAIL_ADDRESS_KEY, String.class)).thenReturn("PENDING");
        assertFalse(service.isVerified(EMAIL_ADDRESS));
    }
    
    @Test
    public void isUnverifiedAndCached() throws Exception {
        when(cacheProvider.getObject(EMAIL_ADDRESS_KEY, String.class)).thenReturn("UNVERIFIED");
        assertFalse(service.isVerified(EMAIL_ADDRESS));
    }
    
    @Test
    public void isVerifiedUncached() {
        doReturn(EmailVerificationStatus.VERIFIED).when(service).getEmailStatus(EMAIL_ADDRESS);
        
        assertTrue(service.isVerified(EMAIL_ADDRESS));
        
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("VERIFIED"), anyInt());
    }
    
    @Test
    public void isPendingUncached() {
        doReturn(EmailVerificationStatus.PENDING).when(service).getEmailStatus(EMAIL_ADDRESS);
        
        assertFalse(service.isVerified(EMAIL_ADDRESS));
        
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("PENDING"), anyInt());
    }
    
    @Test
    public void isUnverifiedUncached() {
        doReturn(EmailVerificationStatus.UNVERIFIED).when(service).getEmailStatus(EMAIL_ADDRESS);
        
        assertFalse(service.isVerified(EMAIL_ADDRESS));
        
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("UNVERIFIED"), anyInt());
    }

    private void verifyAsyncHandler() {
        ArgumentCaptor<EmailVerificationService.AsyncSnsTopicHandler> handlerCaptor = ArgumentCaptor.forClass(
                EmailVerificationService.AsyncSnsTopicHandler.class);
        verify(asyncExecutorService).execute(handlerCaptor.capture());
        assertEquals(EMAIL_ADDRESS, handlerCaptor.getValue().getEmailAddress());
    }
}
