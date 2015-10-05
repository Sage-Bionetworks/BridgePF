package org.sagebionetworks.bridge.models;

import static org.junit.Assert.*;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class ClientInfoTest {

    private static final String VALID_UA_1 = "Asthma/26 (Unknown iPhone; iPhone OS 9.1) BridgeSDK/4";
    private static final String VALID_UA_2 = "Cardio Health/1 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4";
    private static final String VALID_UA_3 = "Unknown Client/14 BridgeJavaSDK/10";
    
    private static final String INVALID_UA_1 = "Amazon Route 53 Health Check Service; ref:c97cd53f-2272-49d6-a8cd-3cd658d9d020; report http://amzn.to/1vsZADi";
    private static final String INVALID_UA_2 = "Integration Tests (Linux/3.13.0-36-generic) BridgeJavaSDK/3";
    private static final String INVALID_UA_3 = "Mozilla/5.0 (Windows NT 5.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2224.3 Safari/537.36";
    
    @Test
    public void hashEquals() {
        EqualsVerifier.forClass(ClientInfo.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void allFieldsCorrect() {
        ClientInfo info = new ClientInfo.Builder()
                .withAppName("AppName")
                .withAppVersion(1)
                .withOsName("OsName")
                .withOsVersion("Version1")
                .withSdkVersion(4).build();
        assertEquals("AppName", info.getAppName());
        assertEquals(1, info.getAppVersion().intValue());
        assertEquals("OsName", info.getOsName());
        assertEquals("Version1", info.getOsVersion());
        assertEquals(4, info.getSdkVersion().intValue());
    }
    
    @Test
    public void missingUserAgentReturnsEmptyClientInf() {
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(null));
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString("   \t"));
    }
    
    @Test
    public void incorrectFormatsReturnEmptyClientInfo() {
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(INVALID_UA_1));
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(INVALID_UA_2));
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(INVALID_UA_3));
    }
    
    @Test
    public void correctFormatReturnsClientInfo() {
        assertNotSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(VALID_UA_1));
        assertNotSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(VALID_UA_2));
    }
    
    @Test
    public void correctFormatWithoutOsReturnsClientInfo() {
        assertNotSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(VALID_UA_3));
    }
    
    @Test
    public void shortFormCorrectlyParsed() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_UA_3);
        assertEquals("Unknown Client", info.getAppName());
        assertEquals(14, info.getAppVersion().intValue());
        assertNull(info.getOsName());
        assertNull(info.getOsVersion());
        assertEquals("BridgeJavaSDK", info.getSdkName());
        assertEquals(10, info.getSdkVersion().intValue());
    }
    
    @Test
    public void longFormCorrectlyParsed() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_UA_1);
        assertEquals("Asthma", info.getAppName());
        assertEquals(26, info.getAppVersion().intValue());
        assertEquals("Unknown iPhone", info.getOsName());
        assertEquals("iPhone OS 9.1", info.getOsVersion());
        assertEquals("BridgeSDK", info.getSdkName());
        assertEquals(4, info.getSdkVersion().intValue());
        
        info = ClientInfo.parseUserAgentString(VALID_UA_2);
        assertEquals("Cardio Health", info.getAppName());
        assertEquals(1, info.getAppVersion().intValue());
        assertEquals("Unknown iPhone", info.getOsName());
        assertEquals("iPhone OS 9.0.2", info.getOsVersion());
        assertEquals("BridgeSDK", info.getSdkName());
        assertEquals(4, info.getSdkVersion().intValue());
    }
    
    @Test
    public void cacheWorks() {
        ClientInfo info1 = ClientInfo.fromUserAgentCache(VALID_UA_1);
        ClientInfo info2 = ClientInfo.fromUserAgentCache(VALID_UA_1);
        
        assertSame(info1, info2);
    }
    
    @Test
    public void cacheWorksWithBadValues() {
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.fromUserAgentCache(null));
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.fromUserAgentCache("   \n"));
    }
    
    @Test
    public void clientWithoutVersionMatchesAnyRange() {
        ClientInfo info = ClientInfo.parseUserAgentString(INVALID_UA_1);
        assertTrue(info.isTargetedAppVersion(3, 3));
    }
    
    @Test
    public void clientWithVersionInRangeSucceeds() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_UA_1);
        assertTrue(info.isTargetedAppVersion(24, 26));
        assertTrue(info.isTargetedAppVersion(26, 27));
    }
    
    @Test
    public void clientWithVersionFilteredOnLowEnd() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_UA_1);
        assertFalse(info.isTargetedAppVersion(27, null));
    }
    
    @Test
    public void clientWithVersionFilteredOnHighEnd() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_UA_1);
        assertFalse(info.isTargetedAppVersion(null, 13));
    }
    
    @Test
    public void clientWithVersionFilteredWithZeroes() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_UA_1);
        assertTrue(info.isTargetedAppVersion(0, 100));
    }
    
}
