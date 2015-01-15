package org.sagebionetworks.bridge.models.surveys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ConstraintTest {

    @Test
    public void constraintsWithUnits() throws Exception {
        IntegerConstraints c = new IntegerConstraints();
        
        c.setUnit(Unit.MILLILITERS);
        c.setMinValue(1d);
        c.setMaxValue(10_000_000d);
        
        String json = BridgeObjectMapper.get().writeValueAsString(c);
        assertTrue(json.contains("\"shortUnit\":\"mL\""));
        
        System.out.println(json);
        
        c = BridgeObjectMapper.get().readValue(json, IntegerConstraints.class);
        assertEquals(DataType.INTEGER, c.getDataType());
        assertEquals(Unit.MILLILITERS, c.getUnit());
        assertEquals(new Long(1L), c.getMinValue());
        assertEquals(new Long(10_000_000L), c.getMaxValue());
    }
    
}
