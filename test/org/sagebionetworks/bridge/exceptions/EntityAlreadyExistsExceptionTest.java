package org.sagebionetworks.bridge.exceptions;

import org.junit.Test;

public class EntityAlreadyExistsExceptionTest {

    /**
     * Some entities are not exposed through the API and when such an internal entity already exists, we cannot return
     * the object the user just submitted to us. The exception should still work.
     */
    @Test(expected = NullPointerException.class)
    public void testExceptionSerializesWithoutEntity() throws Exception {
        new EntityAlreadyExistsException(null, "This should throw an exception");
    }
}
