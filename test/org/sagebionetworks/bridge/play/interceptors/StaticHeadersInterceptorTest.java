package org.sagebionetworks.bridge.play.interceptors;

import static org.mockito.Mockito.verify;

import java.util.Map;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestUtils;

import play.mvc.Http.Response;

@RunWith(MockitoJUnitRunner.class)
public class StaticHeadersInterceptorTest {

    @Mock
    private MethodInvocation invocation;
    
    private StaticHeadersInterceptor interceptor;
    
    @Before
    public void before() {
        interceptor = new StaticHeadersInterceptor();
    }
    
    @Test
    public void addsHeaders() throws Throwable {
        Response response = TestUtils.mockPlay().withRequest(null).withMockResponse().mock();
        
        interceptor.invoke(invocation);
        
        for (Map.Entry<String, String> entry : StaticHeadersInterceptor.HEADERS.entrySet()) {
            verify(response).setHeader(entry.getKey(), entry.getValue());    
        }
    }
}
