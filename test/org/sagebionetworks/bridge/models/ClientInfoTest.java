package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ClientInfoTest {

    private static final String VALID_SHORT_UA_1 = "Unknown Client/14";
    private static final String VALID_SHORT_UA_2 = "App Name: Here/14";
    private static final String VALID_MEDIUM_UA_1 = "Unknown Client/14 BridgeJavaSDK/10";
    private static final String VALID_LONG_UA_1 = "Asthma/26 (Unknown iPhone; iPhone OS/9.1) BridgeSDK/4";
    private static final String VALID_LONG_UA_2 = "Cardio Health/1 (Unknown iPhone; iPhone OS/9.0.2) BridgeSDK/4";
    private static final String VALID_LONG_UA_3 = "Belgium/2 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10";
    private static final String VALID_LONG_UA_1_DEPRECATED = "Asthma/26 (Unknown iPhone; iPhone OS 9.1) BridgeSDK/4";
    private static final String VALID_LONG_UA_2_DEPRECATED = "Cardio Health/1 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4";
    private static final String VALID_LONG_UA_3_DEPRECATED = "Belgium/2 (Motorola Flip-Phone; Android 14) BridgeJavaSDK/10";
    
    private static final String INVALID_UA_1 = "Amazon Route 53 Health Check Service; ref:c97cd53f-2272-49d6-a8cd-3cd658d9d020; report http://amzn.to/1vsZADi";
    private static final String INVALID_UA_2 = "Integration Tests (Linux/3.13.0-36-generic) BridgeJavaSDK/3";
    private static final String INVALID_UA_3 = "Mozilla/5.0 (Windows NT 5.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2224.3 Safari/537.36";
    private static final String INVALID_UA_4 = "Mozilla/5 (Windows NT 5.1) Safari/537"; // Purposefully very ambiguous; still fails.
    private static final String INVALID_UA_5 = "AppName/9098209438734677529830495820945298734059682345";
            
    @Test
    public void hashEquals() {
        EqualsVerifier.forClass(ClientInfo.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void allFieldsCorrect() {
        ClientInfo info = new ClientInfo.Builder()
                .withAppName("AppName")
                .withAppVersion(1)
                .withOsName(IOS)
                .withOsVersion("Version1")
                .withSdkVersion(4).build();
        assertEquals("AppName", info.getAppName());
        assertEquals(1, info.getAppVersion().intValue());
        assertEquals(IOS, info.getOsName());
        assertEquals("Version1", info.getOsVersion());
        assertEquals(4, info.getSdkVersion().intValue());
    }
    
    @Test
    public void missingUserAgentReturnsEmptyClientInfo() {
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(null));
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString("   \t"));
    }
    
    @Test
    public void incorrectFormatsReturnEmptyClientInfo() {
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(INVALID_UA_1));
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(INVALID_UA_2));
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(INVALID_UA_3));
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(INVALID_UA_4));
    }
    
    // This is not a Java integer. It does not match regexp and an unknown client is returned rather than an error
    @Test
    public void numbersTooLongToBeIntegersAreNotParsed() {
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(INVALID_UA_5));
    }
    
    @Test
    public void correctFormatReturnsClientInfo() {
        assertNotSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(VALID_LONG_UA_1));
        assertNotSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(VALID_LONG_UA_2));
    }
    
    @Test
    public void correctFormatWithoutOsReturnsClientInfo() {
        assertNotSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.parseUserAgentString(VALID_MEDIUM_UA_1));
    }
    
    @Test
    public void shortFormCorrectlyParsed() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_SHORT_UA_1);
        assertEquals("Unknown Client", info.getAppName());
        assertEquals(14, info.getAppVersion().intValue());
        assertNull(info.getOsName());
        assertNull(info.getOsVersion());
        assertNull(info.getSdkName());
        assertNull(info.getSdkVersion());
        
        info = ClientInfo.parseUserAgentString(VALID_SHORT_UA_2);
        assertEquals("App Name: Here", info.getAppName());
        assertEquals(14, info.getAppVersion().intValue());
        assertNull(info.getOsName());
        assertNull(info.getOsVersion());
        assertNull(info.getSdkName());
        assertNull(info.getSdkVersion());
    }
    
    @Test
    public void mediumFormCorrectlyParsed() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_MEDIUM_UA_1);
        assertEquals("Unknown Client", info.getAppName());
        assertEquals(14, info.getAppVersion().intValue());
        assertNull(info.getOsName());
        assertNull(info.getOsVersion());
        assertEquals("BridgeJavaSDK", info.getSdkName());
        assertEquals(10, info.getSdkVersion().intValue());
    }
    
    @Test
    public void longFormCorrectlyParsed() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_LONG_UA_1);
        assertEquals("Asthma", info.getAppName());
        assertEquals(26, info.getAppVersion().intValue());
        assertEquals("Unknown iPhone", info.getDeviceName());
        assertEquals(IOS, info.getOsName());
        assertEquals("9.1", info.getOsVersion());
        assertEquals("BridgeSDK", info.getSdkName());
        assertEquals(4, info.getSdkVersion().intValue());
        
        info = ClientInfo.parseUserAgentString(VALID_LONG_UA_2);
        assertEquals("Cardio Health", info.getAppName());
        assertEquals(1, info.getAppVersion().intValue());
        assertEquals("Unknown iPhone", info.getDeviceName());
        assertEquals(IOS, info.getOsName());
        assertEquals("9.0.2", info.getOsVersion());
        assertEquals("BridgeSDK", info.getSdkName());
        assertEquals(4, info.getSdkVersion().intValue());
        
        info = ClientInfo.parseUserAgentString(VALID_LONG_UA_3);
        assertEquals("Belgium", info.getAppName());
        assertEquals(2, info.getAppVersion().intValue());
        assertEquals("Motorola Flip-Phone", info.getDeviceName());
        assertEquals(ANDROID, info.getOsName());
        assertEquals("14", info.getOsVersion());
        assertEquals("BridgeJavaSDK", info.getSdkName());
        assertEquals(10, info.getSdkVersion().intValue());
    }
    
    @Test
    public void deprecatedLongFormCorrectlyParsed() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_LONG_UA_1_DEPRECATED);
        assertEquals("Asthma", info.getAppName());
        assertEquals(26, info.getAppVersion().intValue());
        assertEquals(IOS, info.getOsName());
        assertEquals("9.1", info.getOsVersion());
        assertEquals("BridgeSDK", info.getSdkName());
        assertEquals(4, info.getSdkVersion().intValue());
        
        info = ClientInfo.parseUserAgentString(VALID_LONG_UA_2_DEPRECATED);
        assertEquals("Cardio Health", info.getAppName());
        assertEquals(1, info.getAppVersion().intValue());
        assertEquals(IOS, info.getOsName());
        assertEquals("9.0.2", info.getOsVersion());
        assertEquals("BridgeSDK", info.getSdkName());
        assertEquals(4, info.getSdkVersion().intValue());
        
        info = ClientInfo.parseUserAgentString(VALID_LONG_UA_3_DEPRECATED);
        assertEquals("Belgium", info.getAppName());
        assertEquals(2, info.getAppVersion().intValue());
        assertEquals(ANDROID, info.getOsName());
        assertEquals("14", info.getOsVersion());
        assertEquals("BridgeJavaSDK", info.getSdkName());
        assertEquals(10, info.getSdkVersion().intValue());
    }
    
    @Test
    public void cacheWorks() {
        ClientInfo info1 = ClientInfo.fromUserAgentCache(VALID_LONG_UA_1);
        ClientInfo info2 = ClientInfo.fromUserAgentCache(VALID_LONG_UA_1);
        
        assertSame(info1, info2);
    }
    
    @Test
    public void cacheWorksWithBadValues() {
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.fromUserAgentCache(null));
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.fromUserAgentCache("   \n"));
    }
    
    @Test
    public void testIsSupportedAppVersion_GreaterThanSucceeds() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_LONG_UA_1);
        assertTrue(info.isSupportedAppVersion(25));
    }
    
    @Test
    public void testIsSupportedAppVersion_EqualToSucceeds() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_LONG_UA_1);
        assertTrue(info.isSupportedAppVersion(26));
    }
    
    @Test
    public void testIsSupportedAppVersion_NullMinSucceeds() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_LONG_UA_1);
        assertTrue(info.isSupportedAppVersion(null));
    }
    
    @Test
    public void testIsSupportedAppVersion_LessThanFails() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_LONG_UA_1);
        assertFalse(info.isSupportedAppVersion(27));
    }
    
    @Test
    public void operatingSystemUnknown() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_SHORT_UA_1);
        assertNull(info.getOsName());
    }
    
    @Test
    public void operatingSystemParsed() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_LONG_UA_3);
        assertEquals(ANDROID, info.getOsName());
    }
    
    @Test
    public void deprecatedOperatingSystemParsed() {
        ClientInfo info = ClientInfo.parseUserAgentString(VALID_LONG_UA_3_DEPRECATED);
        assertEquals(ANDROID, info.getOsName());
    }
 }
