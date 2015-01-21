package org.sagebionetworks.bridge.models.surveys;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ConstraintTest {

    @Test
    public void constraintsWithUnits() throws Exception {
        IntegerConstraints c = new IntegerConstraints();
        
        c.setUnit(Unit.MILLILITERS);
        c.setMinValue(1d);
        c.setMaxValue(10000000d);
        
        String json = BridgeObjectMapper.get().writeValueAsString(c);
        c = BridgeObjectMapper.get().readValue(json, IntegerConstraints.class);
        
        assertEquals(DataType.INTEGER, c.getDataType());
        assertEquals(Unit.MILLILITERS, c.getUnit());
        assertEquals(new Double(1.0d), c.getMinValue());
        assertEquals(new Double(10000000d), c.getMaxValue());
    }
    
}
