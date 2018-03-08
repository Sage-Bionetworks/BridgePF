package org.sagebionetworks.bridge.models;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * <p>
 * Parsed representation of the User-Agent header provided by the client, when it is in one of our prescribed formats:
 * </p>
 * 
 * <p>
 * appName/appVersion<br>
 * appName/appVersion sdkName/sdkVersion<br>
 * appName/appVersion (deviceName; osName/osVersion) sdkName/sdkVersion
 * </p>
 * 
 * <p>
 * The full User-Agent header must be provided to enable the filtering of content based on the version of the application 
 * making a request (because versioning information is specific to the name of the OS on which the app is running; we 
 * currently expect either "iPhone OS", "iOS", or "Android", but any value can be used and will work as long as it is 
 * also set in the filtering criteria).
 * </p>
 * 
 * <p>Some examples:</p>
 * 
 * <ul>
 * <li>Melanoma Challenge Application/1</li>
 * <li>Unknown Client/14 BridgeJavaSDK/10</li>
 * <li>Asthma/26 (Unknown iPhone; iPhone OS/9.1) BridgeSDK/4</li>
 * <li>CardioHealth/1 (iPhone 6.0; iPhone OS/9.0.2) BridgeSDK/10</li>
 * </ul>
 * 
 * <p>
 * Other clients with more typical browser user agent strings will be represented by ClientInfo.UNKNOWN_CLIENT. This is
 * a "null" object with all empty fields. Some examples of these headers, from our logs:
 * </p>
 * 
 * <ul>
 * <li>Amazon Route 53 Health Check Service; ref:c97cd53f-2272-49d6-a8cd-3cd658d9d020; report http://amzn.to/1vsZADi</li>
 * <li>Integration Tests (Linux/3.13.0-36-generic) BridgeJavaSDK/3</li>
 * </ul>
 * 
 * <p>
 * ClientInfo is not the end result of a generic user agent string parser. Those are very complicated and we do not need
 * all this information (we always log the user agent string as we receive it from the client, but only use these
 * strings in our system when they are in format specified above).
 * </p>
 *
 */
public final class ClientInfo {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientInfo.class);

    /**
     * A cache of ClientInfo objects that have already been parsed from user agent strings. 
     * We're using this, rather than ConcurrentHashMap, because external clients submit this string, 
     * and thus could create an infinite number of them, starving the server. The cache will protect 
     * against this with its size limit.
     */
    private static final LoadingCache<String, ClientInfo> userAgents = CacheBuilder.newBuilder()
       .maximumSize(500)
       .build(new CacheLoader<String,ClientInfo>() {
            @Override
            public ClientInfo load(String userAgent) throws Exception {
                return ClientInfo.parseUserAgentString(userAgent);
            }
       });

    /**
     * A User-Agent string that does not follow our format is simply an unknown client, and no filtering will be done
     * for such a client. It is represented with a null object that is the ClientInfo object with all null fields. The
     * User-Agent header is still logged exactly as it is retrieved from the request.
     */
    public static final ClientInfo UNKNOWN_CLIENT = new ClientInfo.Builder().build();

    /** Numbers followed by spacing, followed by non-numbers, indicates two stanzas */
    //private static final String TWO_STANZA_REGEXP = ".*[0-9]+\\s+[^0-9]+.*";
    //private static final String STANZA_SPLIT_REGEXP = "(?<=[0-9])\\s(?=([^0-9]))";
    private static final String TWO_STANZA_REGEXP = ".*[0-9]+\\s+.*";
    private static final String STANZA_SPLIT_REGEXP = "(?<=[0-9])\\s(?=(.*))";
    private static final String OLD_OS_FORMAT_REGEXP = "[^/]+\\s[\\d\\.]+";
    private static final String DIGITS_REGEXP = "^\\s*[0-9]+\\s*$";
    private static final String SEMANTIC_VERSION_REGEXP = "^\\s*[0-9\\.]+\\s*$";

    private final String appName;
    private final Integer appVersion;
    private final String deviceName;
    private final String osName;
    private final String osVersion;
    private final String sdkName;
    private final Integer sdkVersion;

    @JsonCreator
    private ClientInfo(@JsonProperty("appName") String appName, @JsonProperty("appVersion") Integer appVersion,
            @JsonProperty("deviceName") String deviceName, @JsonProperty("osName") String osName,
            @JsonProperty("osVersion") String osVersion, @JsonProperty("sdkName") String sdkName,
            @JsonProperty("sdkVersion") Integer sdkVersion) {
        this.appName = appName;
        this.appVersion = appVersion;
        this.deviceName = deviceName;
        this.osName = osName;
        this.osVersion = osVersion;
        this.sdkName = sdkName;
        this.sdkVersion = sdkVersion;
    }

    public String getAppName() {
        return appName;
    }

    public Integer getAppVersion() {
        return appVersion;
    }
    
    public String getDeviceName() {
        return deviceName;
    }

    public String getOsName() {
        return osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public String getSdkName() {
        return sdkName;
    }

    public Integer getSdkVersion() {
        return sdkVersion;
    }
    
    public boolean isSupportedAppVersion(Integer minSupportedVersion) {
        // If both the appVersion and minSupportedVersion are defined, check that the appVersion is
        // greater than or equal to the minSupportedVersion
        return (appVersion == null || minSupportedVersion == null || appVersion >= minSupportedVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appName, appVersion, deviceName, osName, osVersion, sdkName, sdkVersion);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ClientInfo other = (ClientInfo) obj;
        return Objects.equals(appName, other.appName) && Objects.equals(appVersion, other.appVersion)
                && Objects.equals(osName, other.osName) && Objects.equals(osVersion, other.osVersion)
                && Objects.equals(sdkName, other.sdkName) && Objects.equals(sdkVersion, other.sdkVersion) 
                && Objects.equals(deviceName, other.deviceName);
    }

    @Override
    public String toString() {
        return "ClientInfo [appName=" + appName + ", appVersion=" + appVersion + ", deviceName=" + deviceName
                + ", osName=" + osName + ", osVersion=" + osVersion + ", sdkName=" + sdkName + ", sdkVersion="
                + sdkVersion + "]";
    }

    public static class Builder {
        private String appName;
        private Integer appVersion;
        private String deviceName;
        private String osName;
        private String osVersion;
        private String sdkName;
        private Integer sdkVersion;

        public Builder withAppName(String appName) {
            this.appName = appName;
            return this;
        }
        public Builder withAppVersion(Integer appVersion) {
            this.appVersion = appVersion;
            return this;
        }
        public Builder withDeviceName(String deviceName) {
            this.deviceName = deviceName;
            return this;
        }
        public Builder withOsName(String osName) {
            this.osName = osName;
            return this;
        }
        public Builder withOsVersion(String osVersion) {
            this.osVersion = osVersion;
            return this;
        }
        public Builder withSdkName(String sdkName) {
            this.sdkName = sdkName;
            return this;
        }
        public Builder withSdkVersion(Integer sdkVersion) {
            this.sdkVersion = sdkVersion;
            return this;
        }
        /**
         * It's valid to have a client info object with no fields, if the
         * User-Agent header is not in our prescribed format.
         */
        public ClientInfo build() {
            if (OperatingSystem.SYNONYMS.containsKey(osName)) {
                osName = OperatingSystem.SYNONYMS.get(osName);
            }
            return new ClientInfo(appName, appVersion, deviceName, osName, osVersion, sdkName, sdkVersion);
        }

    }
    
    /**
     * Get a ClientInfo object given a User-Agent header string. These values are cached and 
     * headers that are not in the prescribed format return an empty client info object.
     * @param userAgent
     * @return
     */
    public static ClientInfo fromUserAgentCache(String userAgent) {
        // The "null" string comes up in tests, and there's no point in parsing it
        if (!StringUtils.isBlank(userAgent) && !"null".equals(userAgent)) {
            try {
                return userAgents.get(userAgent);    
            } catch(ExecutionException e) {
                // This should not happen, the CacheLoader doesn't throw exceptions
                // Log it and return UNKNOWN_CLIENT
                LOGGER.error(e.getMessage(), e);
            }
        }
        return UNKNOWN_CLIENT;
    }
    
    static ClientInfo parseUserAgentString(String userAgent) {
        if (StringUtils.isBlank(userAgent) || invalidFormat(userAgent)) {
            return ClientInfo.UNKNOWN_CLIENT;
        }
        ClientInfo.Builder builder = new ClientInfo.Builder();
        String[] stanzas = userAgent.split("[()]");
        if (stanzas.length == 3) {
            // All three components are present.
            parseAppStanza(builder, stanzas[0]);
            parseDeviceStanza(builder, stanzas[1]);
            parseSdkStanza(builder, stanzas[2]);
        } else if (stanzas.length == 2) {
            // This is the left or left and center stanza
            parseAppStanza(builder, stanzas[0]);
            parseDeviceStanza(builder, stanzas[1]);
        } else if (stanzas.length == 1) {
            // This is the app or sdk stanza, or both
            if (stanzas[0].matches(TWO_STANZA_REGEXP)) {
                // It's both
                String[] components = stanzas[0].split(STANZA_SPLIT_REGEXP);
                parseAppStanza(builder, components[0]);
                parseSdkStanza(builder, components[1]);
            } else {
                // It's one, we assume it's the app stanza
                parseAppStanza(builder, stanzas[0]);
            }
        }
        ClientInfo info = builder.build();
        
        // Return singleton instance for all unknown clients. Would like to catch this in the builder,
        // but the order of static initialization is not correct.
        if (info.getAppName() == null && info.getAppVersion() == null && info.getDeviceName() == null
                && info.getOsName() == null && info.getOsVersion() == null && info.getSdkName() == null
                && info.getSdkVersion() == null) {
            return ClientInfo.UNKNOWN_CLIENT;
        }
        return info;
    }
    
    /**
     * Verifies a few things about the string:
     * 
     * - shouldn't be more than one set of parentheses (and they are matched)
     * - if there's a semicolon, there should only be one and there should be parentheses
     * - shouldn't be more than 3 slashes
     */
    private static boolean invalidFormat(String userAgent) {
        // Format: a/b (c; d/e) f/g
        //    : / ( /
        // 1/ : ( /
        // (  : ; / )      
        // ;  : / )
        // 2/ : )
        // )  : /
        // 3/ : 
        char lastSeparator = 0;
        int slashes = 0;
        for (int i=0; i < userAgent.length(); i++) {
            char oneChar = userAgent.charAt(i);
            
            if (!inSet(oneChar, '(', ')', ';', '/')) {
                continue;
            }
            if ((lastSeparator == 0 && !inSet(oneChar, '/', '(')) ||
                (lastSeparator == '(' && !inSet(oneChar, ';', '/', ')')) ||
                (lastSeparator == ';' && !inSet(oneChar, ')', '/')) ||
                (lastSeparator == ')' && !inSet(oneChar, '/'))) {
                return true;
            } else if (lastSeparator == '/') {
                ++slashes;
                
                if ((slashes == 1 && !inSet(oneChar, '(', '/', ')')) ||
                    (slashes == 2 && !inSet(oneChar, ')', '/')) || 
                    (slashes > 2)) {
                    return true;
                }
                /*
                if (slashes == 1 && !inSet(oneChar, '(', '/', ')')) {
                    return true;
                } else if (slashes == 2 && !inSet(oneChar, ')', '/')) {
                    return true;
                } else if (slashes > 2) {
                    return true;
                }*/
            }
            // Basically once you have a device stanza and it's completed
            // we want to ensure you are looking for the last slash.
            if (oneChar == ')') {
                ++slashes;
            }
            lastSeparator = oneChar;
        }
        return false;
    }
    
    private static boolean inSet(char character, char... set) {
        for (char testCharacter : set) {
            if (character == testCharacter) {
                return true;
            }
        }
        return false;
    }
    
    private static void parseAppStanza(ClientInfo.Builder builder, String stanza) {
        if (StringUtils.isBlank(stanza)) {
            return;
        }
        String[] components = stanza.split("/");
        if (components.length == 2) {
            if (!components[0].matches(DIGITS_REGEXP)) {
                builder.withAppName(parseString(components[0]));    
            }
            builder.withAppVersion(parseInteger(components[1]));
        } else if (components.length == 1) {
            // if it's a number, it's a version, otherwise it's the name.
            if (components[0].matches(DIGITS_REGEXP)) {
                builder.withAppVersion(parseInteger(components[0]));
            } else {
                builder.withAppName(parseString(components[0]));
            }
        }
    }
    
    private static void parseDeviceStanza(ClientInfo.Builder builder, String stanza) {
        String[] components = stanza.split(";");
        if (components.length == 2) {
            builder.withDeviceName(parseString(components[0]));
            parseOsStanza(builder, components[1].trim());
        } else if (components.length == 1) {
            parseOsStanza(builder, components[0].trim());
        }
    }
    
    private static void parseOsStanza(ClientInfo.Builder builder, String stanza) {
        // The old format did not have a slash in it. We have to account for that.
        String[] components = null;
        if (stanza.matches(OLD_OS_FORMAT_REGEXP)) {
            components = new String[] {
                stanza.substring(0, stanza.lastIndexOf(" ")),
                stanza.substring(stanza.lastIndexOf(" ")),
            };
        } else {
            components = stanza.split("/");    
        }
        if (components.length == 2) {
            builder.withOsName(parseString(components[0]));
            builder.withOsVersion(parseString(components[1]));
        } else if (components.length == 1) {
            // if it's a number, it's a version, otherwise it's the name.
            if (components[0].trim().matches(SEMANTIC_VERSION_REGEXP)) {
                builder.withOsVersion(parseString(components[0]));
            } else {
                builder.withOsName(parseString(components[0]));
            }
        }
    }
    
    private static void parseSdkStanza(ClientInfo.Builder builder, String stanza) {
        String[] sdkComponents = stanza.split("/");
        if (sdkComponents.length == 2) {
            if (!sdkComponents[0].matches(DIGITS_REGEXP)) {
                builder.withSdkName(parseString(sdkComponents[0]));    
            }
            builder.withSdkVersion(parseInteger(sdkComponents[1]));
        } else if (sdkComponents.length == 1) {
            if (sdkComponents[0].matches(DIGITS_REGEXP)) {
                builder.withSdkVersion(parseInteger(sdkComponents[0]));
            } else {
                builder.withSdkName(parseString(sdkComponents[0]));
            }
        }
    }
    
    private static String parseString(String value) {
        // should not contain characters we consider special
        if (StringUtils.isBlank(value.trim()) || value.matches(".*[;\\(\\)]+.*")) { 
            return null;
        }
        return value.trim();
    }
    
    private static Integer parseInteger(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch(NumberFormatException e) {
            return null;
        }
    }    

}
