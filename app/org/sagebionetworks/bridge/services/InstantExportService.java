package org.sagebionetworks.bridge.services;


import javax.annotation.Nonnull;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * Interface for export current study instantly. Mainly for testing. Current implementation uses SQS (see {@link
 * InstantExportViaSqsService), but this is an interface to allow different implementations.
 */
public interface InstantExportService {
    /**
     * Kicks off an asynchronous request to request Bridge Exporter to export current study to Synapse
     *
     * @param studyIdentifier
     *         study identifier of the logged in user
     * @throws JsonProcessingException
     *         if converting the request to JSON fails
     */
    void export(@Nonnull StudyIdentifier studyIdentifier)
            throws JsonProcessingException;
}
