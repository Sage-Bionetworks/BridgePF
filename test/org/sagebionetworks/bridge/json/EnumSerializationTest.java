package org.sagebionetworks.bridge.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;

import com.fasterxml.jackson.databind.ObjectMapper;

public class EnumSerializationTest {
    
    public static class EnumHolder {
        private ScheduleType type;
        public ScheduleType getType() {
            return type;
        }
        public void setType(ScheduleType type) {
            this.type = type;
        }
    }
    
    private ObjectMapper mapper;
    
    @Before
    public void before() {
        mapper = BridgeObjectMapper.get();
    }
    
    @Test
    public void canRoundTripObjectWithEnumeration() throws Exception {
        
        EnumHolder holder = new EnumHolder();
        holder.setType(ScheduleType.ONCE);
        
        String json = mapper.writeValueAsString(holder);
        assertEquals("{\"type\":\"once\"}", json);
        
        holder = mapper.readValue(json, EnumSerializationTest.EnumHolder.class);
        assertEquals(ScheduleType.ONCE, holder.getType());
    }
    
    @Test
    public void canRoundtripNullEnum() throws Exception {
        EnumHolder holder = new EnumHolder();
        
        String json = mapper.writeValueAsString(holder);
        assertEquals("{}", json);
        
        holder = mapper.readValue(json, EnumSerializationTest.EnumHolder.class);
        assertNull(holder.getType());
    }
    
    @Test
    public void canDeserializeUpperCaseEnums() throws Exception {
        String json = "{\"type\":\"ONCE\"}";
        
        EnumHolder holder = mapper.readValue(json, EnumSerializationTest.EnumHolder.class);
        assertEquals(ScheduleType.ONCE, holder.getType());
    }

}
