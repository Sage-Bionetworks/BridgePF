package org.sagebionetworks.bridge.upload;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.DateUtils;

/** Utility class that contains static utility methods for handling uploads. */
public class UploadUtil {
    /**
     * For some reason, the iOS apps are sometimes sending timestamps in form "YYYY-MM-DD hh:mm:ss +ZZZZ", which is
     * non-ISO-compliant and can't be parsed by JodaTime. We'll need to convert these to ISO format, generally
     * "YYYY-MM-DDThh:mm:ss+ZZZZ".
     *
     * @param timestampStr
     *         raw timestamp string
     * @return parsed DateTime, or null if it couldn't be parsed
     */
    // TODO: Remove this hack when it's no longer needed.
    public static DateTime parseIosTimestamp(String timestampStr) {
        if (StringUtils.isBlank(timestampStr)) {
            return null;
        }

        // Detect if this is iOS non-standard format by checking to see if the 10th char is a space.
        if (timestampStr.charAt(10) == ' ') {
            // Attempt to convert this by replacing the 10th char with a T and then stripping out all spaces.
            timestampStr = timestampStr.substring(0, 10) + 'T' + timestampStr.substring(11);
            timestampStr = timestampStr.replaceAll("\\s+", "");
        }

        try {
            return DateUtils.parseISODateTime(timestampStr);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
