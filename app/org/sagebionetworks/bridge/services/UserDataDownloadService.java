package org.sagebionetworks.bridge.services;

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
     */
    void requestUserData(StudyIdentifier studyIdentifier, String userId, DateRange dateRange)
            throws JsonProcessingException;
}
