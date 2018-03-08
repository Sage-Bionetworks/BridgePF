package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ClientInfoTest {

    private static final String UA = "Asthma/26 (Unknown iPhone; iPhone OS/9.1) BridgeSDK/4";
    
    @Test
    public void hashEquals() {
        EqualsVerifier.forClass(ClientInfo.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void verifyParsing() {
        // One stanza is interpreted to be the application stanza.
        assertClientInfo("appName", "appName", null, null, null, null, null, null);
        assertClientInfo("appName/", "appName", null, null, null, null, null, null);
        assertClientInfo("appName/3", "appName", 3, null, null, null, null, null);
        assertClientInfo("/3", null, 3, null, null, null, null, null);
        assertClientInfo("3", null, 3, null, null, null, null, null);
        
        // Parenthesese can indicate which side the free string is on
        // 1 central stanza:
        assertClientInfo("(osName)", null, null, null, "osName", null, null, null);
        assertClientInfo("(osName/)", null, null, null, "osName", null, null, null);
        assertClientInfo("(osName/4.0.0)", null, null, null, "osName", "4.0.0", null, null);
        assertClientInfo("(/4.0.0)", null, null, null, null, "4.0.0", null, null);
        assertClientInfo("(osName)", null, null, null, "osName", null, null, null);
        assertClientInfo("(deviceName; osName)", null, null, "deviceName", "osName", null, null, null);
        assertClientInfo("(deviceName; osName/)", null, null, "deviceName", "osName", null, null, null);
        assertClientInfo("(deviceName; osName/4.0.0)", null, null, "deviceName", "osName", "4.0.0", null, null);
        assertClientInfo("(deviceName; /4.0.0)", null, null, "deviceName", null, "4.0.0", null, null);
        assertClientInfo("(deviceName; 4.0.0)", null, null, "deviceName", null, "4.0.0", null, null);
        
        // The left side stanza:
        assertClientInfo("appName/3 (osName/4.0.0)", "appName", 3, null, "osName", "4.0.0", null, null);
        
        // Or the right side stanza. Verify all combinations of that stanza as well.
        assertClientInfo("(deviceName; osName/4.0.0) sdkName", null, null, "deviceName", "osName", "4.0.0", "sdkName", null);
        assertClientInfo("(deviceName; osName/4.0.0) /5", null, null, "deviceName", "osName", "4.0.0", null, 5);
        assertClientInfo("(deviceName; osName/4.0.0) sdkName/5", null, null, "deviceName", "osName", "4.0.0", "sdkName", 5);
        assertClientInfo("appName/1.0 (deviceName; osName/4.0.0) sdkName/5.0.0", "appName", null, "deviceName",
                "osName", "4.0.0", "sdkName", null);
        
        // Spacing does not break the parser
        assertClientInfo("appName / 1 ( deviceName ; osName / 4) sdkName / 5", "appName", 1, "deviceName",
                "osName", "4", "sdkName", 5);
        
        // this can now get pretty wacky, we parse what we can, ignoring the rest
        assertClientInfo("/3 (; iOS 10) SDK", null, 3, null, "iPhone OS", "10", "SDK", null);
        assertClientInfo("/3 (; iOS 10)", null, 3, null, "iPhone OS", "10", null, null);
        assertClientInfo("(;iOS 10) SDK", null, null, null, "iPhone OS", "10", "SDK", null);
        assertClientInfo("/3 (;; iOS 10)", null, null, null, null, null, null, null);
        assertClientInfo("/app (iOS) 10", null, null, null, "iPhone OS", null, null, 10);
        assertClientInfo("/app", null, null, null, null, null, null, null);
        assertClientInfo("10//10", null, null, null, null, null, null, null);
        assertClientInfo("10 10", null, 10, null, null, null, null, 10);
        assertClientInfo("/7 (Android/10) /5", null, 7, null, "Android", "10", null, 5);
        assertClientInfo("1/2 3/4", null, 2, null, null, null, null, 4);
        assertClientInfo("1/2", null, 2, null, null, null, null, null);
        assertClientInfo("app/3 (Device; /2.0.0) sdk/4", "app", 3, "Device", null, "2.0.0", "sdk", 4);
        assertClientInfo("AppName/1 (Device Name; iPhone OS) BridgeJavaSDK/3",
                "AppName", 1, "Device Name", "iPhone OS", null, "BridgeJavaSDK", 3);
        assertClientInfo("appName (deviceName; osName/osVersion) sdkName", "appName", null, "deviceName", "osName",
                "osVersion", "sdkName", null);
        
        // Also try some error conditions...
        assertClientInfo("/ (; /) /", null, null, null, null, null, null, null);
        assertClientInfo("/ () /", null, null, null, null, null, null, null);
        assertClientInfo("()", null, null, null, null, null, null, null);
        assertClientInfo(null, null, null, null, null, null, null, null);
        assertClientInfo("   \t", null, null, null, null, null, null, null);
        assertClientInfo("(test) (tones)", null, null, null, null, null, null, null);
        assertClientInfo("foo/bar d/e (c; a/b)", null, null, null, null, null, null, null);
        assertClientInfo("(c/d; a/b)", null, null, null, null, null, null, null);
        
        // All the original test strings pass
        assertClientInfo("Unknown Client/14", "Unknown Client", 14, null, null, null, null, null);
        assertClientInfo("App Name: Here/14", "App Name: Here", 14, null, null, null, null, null);
        assertClientInfo("Unknown Client/14 BridgeJavaSDK/10", "Unknown Client", 14, null, null, null, "BridgeJavaSDK",
                10);
        assertClientInfo("Asthma/26 (Unknown iPhone; iPhone OS/9.1) BridgeSDK/4", "Asthma", 26, "Unknown iPhone",
                "iPhone OS", "9.1", "BridgeSDK", 4);
        assertClientInfo("Cardio Health/1 (Unknown iPhone; iPhone OS/9.0.2) BridgeSDK/4", "Cardio Health", 1,
                "Unknown iPhone", "iPhone OS", "9.0.2", "BridgeSDK", 4);
        assertClientInfo("Belgium/2 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10", "Belgium", 2,
                "Motorola Flip-Phone", "Android", "14", "BridgeJavaSDK", 10);
        assertClientInfo("Asthma/26 (Unknown iPhone; iPhone OS 9.1) BridgeSDK/4", "Asthma", 26, "Unknown iPhone",
                "iPhone OS", "9.1", "BridgeSDK", 4);
        assertClientInfo("Cardio Health/1 (Unknown iPhone; iPhone OS 9.0.2) BridgeSDK/4", "Cardio Health", 1,
                "Unknown iPhone", "iPhone OS", "9.0.2", "BridgeSDK", 4);
        
        // Here are some odd ones we can now parse
        assertClientInfo("PostmanRuntime/7.1.1", "PostmanRuntime", null, null, null, null, null, null);
        assertClientInfo("PostmanRuntime/", "PostmanRuntime", null, null, null, null, null, null);
        assertClientInfo("/7.1.1", null, null, null, null, null, null, null);
        assertClientInfo("/7", null, 7, null, null, null, null, null);
        assertClientInfo("Mozilla/5 (Windows NT 5.1) Safari/537", "Mozilla", 5, null, "Windows NT", "5.1", "Safari",
                537);
        assertClientInfo("Integration Tests (Linux/3.13.0-36-generic) BridgeJavaSDK/3", "Integration Tests", null, null,
                "Linux", "3.13.0-36-generic", "BridgeJavaSDK", 3);
        
        // This silently fails to treat this huge number as an integer
        assertClientInfo("AppName/9098209438734677529830495820945298734059682345", "AppName", null, null, null, null,
                null, null);
        
        // These are unstructured UAs that don't follow a format we recognize.
        assertClientInfo(
                "Amazon Route 53 Health Check Service; ref:c97cd53f-2272-49d6-a8cd-3cd658d9d020; report http://amzn.to/1vsZADi",
                null, null, null, null, null, null, null);
        assertClientInfo(
                "Mozilla/5.0 (Windows NT 5.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2224.3 Safari/537.36",
                null, null, null, null, null, null, null);
        assertClientInfo(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.13; rv:58.0) Gecko/20100101 Firefox/58.0",
                null, null, null, null, null, null, null);
        // I made this up so it would break the parser.
        assertClientInfo("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_3) Chrome/64.0.3282.186 Safari/537.36", 
                null, null, null, null, null, null, null);
        assertClientInfo(";;", null, null, null, null, null, null, null);
        assertClientInfo(";(", null, null, null, null, null, null, null);
        assertClientInfo("))", null, null, null, null, null, null, null);
        assertClientInfo(")(", null, null, null, null, null, null, null);
        assertClientInfo(");", null, null, null, null, null, null, null);
        assertClientInfo("/;", null, null, null, null, null, null, null);
        assertClientInfo("//(", null, null, null, null, null, null, null);
        assertClientInfo("///", null, null, null, null, null, null, null);
        assertClientInfo("()/()", null, null, null, null, null, null, null);
        
        // Never seen in the wild, but made to fail our parser (and now passes)
        assertClientInfo("appName; 10 (something/10/test)", null, null, null, null, null, null, null);
        
        // Finally, for the supremely lazy client, this is all that is needed to trigger server-side matching:
        assertClientInfo("20 (iOS)", null, 20, null, "iPhone OS", null, null, null);
    }

    private void assertClientInfo(String userAgentString, String appName, Integer appVersion, String deviceName,
            String osName, String osVersion, String sdkName, Integer sdkVersion) {
        ClientInfo info = ClientInfo.parseUserAgentString(userAgentString);
        assertEquals(appName, info.getAppName());
        assertEquals(appVersion, info.getAppVersion());
        assertEquals(deviceName, info.getDeviceName());
        assertEquals(osName, info.getOsName());
        assertEquals(osVersion, info.getOsVersion());
        assertEquals(sdkName, info.getSdkName());
        assertEquals(sdkVersion, info.getSdkVersion());
    }

    @Test
    public void cacheWorks() {
        ClientInfo info1 = ClientInfo.fromUserAgentCache(UA);
        ClientInfo info2 = ClientInfo.fromUserAgentCache(UA);
        
        assertSame(info1, info2);
    }
    
    @Test
    public void cacheWorksWithBadValues() {
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.fromUserAgentCache(null));
        assertSame(ClientInfo.UNKNOWN_CLIENT, ClientInfo.fromUserAgentCache("   \n"));
    }
    
    @Test
    public void testIsSupportedAppVersion_GreaterThanSucceeds() {
        ClientInfo info = ClientInfo.parseUserAgentString(UA);
        assertTrue(info.isSupportedAppVersion(25));
    }
    
    @Test
    public void testIsSupportedAppVersion_EqualToSucceeds() {
        ClientInfo info = ClientInfo.parseUserAgentString(UA);
        assertTrue(info.isSupportedAppVersion(26));
    }
    
    @Test
    public void testIsSupportedAppVersion_NullMinSucceeds() {
        ClientInfo info = ClientInfo.parseUserAgentString(UA);
        assertTrue(info.isSupportedAppVersion(null));
    }
    
    @Test
    public void testIsSupportedAppVersion_LessThanFails() {
        ClientInfo info = ClientInfo.parseUserAgentString(UA);
        assertFalse(info.isSupportedAppVersion(27));
    }    
    @Test
    public void returnsUknownClient() {
        ClientInfo info = ClientInfo.parseUserAgentString("/7.1.1.");
        assertSame(ClientInfo.UNKNOWN_CLIENT, info);
    }
    
    @Test
    public void allFieldsCorrect() {
        ClientInfo info = new ClientInfo.Builder()
                .withAppName("AppName")
                .withAppVersion(1)
                .withDeviceName("Happy Jagger")
                .withOsName(IOS)
                .withOsVersion("Version1")
                .withSdkName("BridgeSDK")
                .withSdkVersion(4).build();
        assertEquals("AppName", info.getAppName());
        assertEquals(1, info.getAppVersion().intValue());
        assertEquals(IOS, info.getOsName());
        assertEquals("Version1", info.getOsVersion());
        assertEquals(4, info.getSdkVersion().intValue());
    }
    
 }
