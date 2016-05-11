package org.sagebionetworks.bridge.services;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * Interface for user data download requests. Current implementation uses SQS (see {@link
 * UserDataDownloadViaSqsService), but this is an interface to allow different implementations.
 */
public interface UserDataDownloadService {
    /**
     * Kicks off an asynchronous request to gather user data for the logged in user, with data from the specified date
     * range (inclusive).
     *
     * @param studyIdentifier
     *         study identifier of the logged in user
     * @param user
     *         the logged in user
     * @param dateRange
     *         date range (inclusive) of the user's data to gather
     * @throws JsonProcessingException
     *         if converting the request to JSON fails
     */
    void requestUserData(@Nonnull StudyIdentifier studyIdentifier, @Nonnull String email, @Nonnull DateRange dateRange)
            throws JsonProcessingException;
}
