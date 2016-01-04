package org.sagebionetworks.bridge.upload;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.json.DateUtils;

/** Utility class that contains static utility methods for handling uploads. */
public class UploadUtil {
    private static final Logger logger = LoggerFactory.getLogger(UploadUtil.class);

    /**
     * <p>
     * For some reason, the iOS are inserting arbitrary times into calendar dates. We need to convert them back to
     * calendar dates. This method simply detects if the string is too long and then truncates it into 10 characters,
     * then tries to parse the result.
     * </p>
     * <p>
     * Note that converting timestamps into calendar dates is inherently ambiguous, since a single timestamp can
     * represent two (or more) calendar dates. Example: 2015-12-23T00:00Z vs 2015-12-22T16:00-08:00
     * </p>
     *
     * @param dateStr
     *         string to parse into a calendar date
     * @return parsed LocalDate, or null if it couldn't be parsed
     */
    public static LocalDate parseIosCalendarDate(String dateStr) {
        if (StringUtils.isBlank(dateStr)) {
            return null;
        }

        if (dateStr.length() > 10) {
            logger.warn("Non-standard calendar date in upload data: " + dateStr);
            dateStr = dateStr.substring(0, 10);
        }

        try {
            return DateUtils.parseCalendarDate(dateStr);
        } catch (IllegalArgumentException ex) {
            logger.warn("Malformatted calendar date in upload data: " + dateStr);
            return null;
        }
    }

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
        // Timestamps must have at least 11 chars to represent the date, at minimum.
        if (StringUtils.isBlank(timestampStr) || timestampStr.length() < 11) {
            return null;
        }

        // Detect if this is iOS non-standard format by checking to see if the 10th char is a space.
        if (timestampStr.charAt(10) == ' ') {
            // Log something, so we can keep track of how often this happens.
            logger.warn("Non-standard timestamp in upload data: " + timestampStr);

            // Attempt to convert this by replacing the 10th char with a T and then stripping out all spaces.
            timestampStr = timestampStr.substring(0, 10) + 'T' + timestampStr.substring(11);
            timestampStr = timestampStr.replaceAll("\\s+", "");
        }

        try {
            return DateUtils.parseISODateTime(timestampStr);
        } catch (IllegalArgumentException ex) {
            logger.warn("Malformatted timestamp in upload data: " + timestampStr);
            return null;
        }
    }
}
