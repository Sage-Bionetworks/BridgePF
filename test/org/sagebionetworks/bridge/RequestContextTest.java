package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class RequestContextTest {

    private static final Set<String> SUBSTUDIES = ImmutableSet.of("testA", "testB");

    @Test
    public void nullObjectReturnsNullAndEmpty() { 
        RequestContext nullContext = new RequestContext.Builder().withRequestId(null).withCallerStudyId(null)
                .withCallerSubstudies(null).build();
        
        assertNull(nullContext.getId());
        assertTrue(nullContext.getCallerSubstudies().isEmpty());
        assertNull(nullContext.getCallerStudyId());
    }

    @Test
    public void test() { 
        RequestContext context = new RequestContext.Builder().withRequestId("requestId")
                .withCallerStudyId(TestConstants.TEST_STUDY).withCallerSubstudies(SUBSTUDIES)
                .build();
        assertEquals("requestId", context.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, context.getCallerStudyId());
        assertEquals(SUBSTUDIES, context.getCallerSubstudies());
    }
}
