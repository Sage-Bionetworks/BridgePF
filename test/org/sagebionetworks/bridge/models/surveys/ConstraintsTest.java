package org.sagebionetworks.bridge.models.surveys;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ConstraintsTest {

    /**
     * It is possible to configure the constraints sub-typing information such that dataType 
     * is serialized twice, which is invalid. Test here that this no longer happens.
     * @throws Exception
     */
    @Test
    public void constraintsDoNotSerializeDataTypeTwice() throws Exception {
        DateTimeConstraints constraints = new DateTimeConstraints();
        constraints.setEarliestValue(DateTime.parse("2015-01-01T10:10:10-07:00"));
        constraints.setLatestValue(DateTime.parse("2015-12-31T10:10:10-07:00"));

        String json = BridgeObjectMapper.get().writeValueAsString(constraints);
        assertEquals(json.indexOf("\"dataType\""), json.lastIndexOf("\"dataType\""));
    }

}
