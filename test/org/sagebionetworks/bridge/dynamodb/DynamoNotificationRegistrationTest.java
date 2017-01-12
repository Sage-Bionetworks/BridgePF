package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

import com.fasterxml.jackson.databind.JsonNode;

public class DynamoNotificationRegistrationTest {
    
    private static final long CREATED_ON = DateTime.parse("2017-01-10T20:29:14.319Z").getMillis();
    private static final long MODIFIED_ON = DateTime.parse("2017-01-11T20:29:14.319Z").getMillis();
    
    @Test
    public void canSerialize() throws Exception {
        NotificationRegistration reg = NotificationRegistration.create();
        reg.setHealthCode("healthCode");
        reg.setGuid("ABC");
        reg.setEndpointARN("anEndpointARN");
        reg.setDeviceId("aDeviceId");
        reg.setOsName(OperatingSystem.ANDROID);
        reg.setCreatedOn(CREATED_ON);
        reg.setModifiedOn(MODIFIED_ON);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(reg);
        assertEquals("ABC", node.get("guid").asText());
        assertEquals("aDeviceId", node.get("deviceId").asText());
        assertEquals("Android", node.get("osName").asText());
        assertEquals("2017-01-10T20:29:14.319Z", node.get("createdOn").asText());
        assertEquals("2017-01-11T20:29:14.319Z", node.get("modifiedOn").asText());
        assertEquals("NotificationRegistration", node.get("type").asText());
        assertEquals(6, node.size()); // and no other fields like healthCode;
        
        // In creating a registration, the deviceId, osName and sometimes the  guid, so these must 
        // deserialize correctly. Other fields will be set on the server.
        String json = TestUtils.createJson("{'guid':'ABC','deviceId':'aDeviceId','osName':'iPhone OS'}");
        
        NotificationRegistration deser = BridgeObjectMapper.get().readValue(json, NotificationRegistration.class);
        assertEquals("ABC", deser.getGuid());
        assertEquals("aDeviceId", deser.getDeviceId());
        assertEquals("iPhone OS", deser.getOsName());
    }
    
}
