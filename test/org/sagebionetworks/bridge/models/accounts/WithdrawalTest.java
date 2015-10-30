package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class WithdrawalTest {

    @Test
    public void canSerialize() throws Exception {
        String json = "{\"reason\":\"reasons\"}";
        Withdrawal withdrawal = BridgeObjectMapper.get().readValue(json, Withdrawal.class);
        assertEquals("reasons", withdrawal.getReason());
        
        json = "{\"reason\":null}";
        withdrawal = BridgeObjectMapper.get().readValue(json, Withdrawal.class);
        assertNull(withdrawal.getReason());
        
        json = "{}";
        withdrawal = BridgeObjectMapper.get().readValue(json, Withdrawal.class);
        assertNull(withdrawal.getReason());
    }
}
