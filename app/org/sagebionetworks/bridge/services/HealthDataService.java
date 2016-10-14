package org.sagebionetworks.bridge.services;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.models.healthdata.*;
import org.sagebionetworks.bridge.validators.RecordExportStatusRequestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.HealthDataAttachmentDao;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.validators.HealthDataRecordValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.validation.Validator;


/** Service handler for health data APIs. */
@Component
public class HealthDataService {
    private HealthDataAttachmentDao healthDataAttachmentDao;
    private HealthDataDao healthDataDao;

    private final static int MAX_NUM_RECORD_IDS = 100;
    private static final Validator exporterStatusValidator = new RecordExportStatusRequestValidator();

    /** Health data attachment DAO. This is configured by Spring. */
    @Autowired
    public void setHealthDataAttachmentDao(HealthDataAttachmentDao healthDataAttachmentDao) {
        this.healthDataAttachmentDao = healthDataAttachmentDao;
    }

    /** Health data DAO. This is configured by Spring. */
    @Autowired
    public void setHealthDataDao(HealthDataDao healthDataDao) {
        this.healthDataDao = healthDataDao;
    }

    /* HEALTH DATA RECORD APIs */

    /**
     * Creates (or updates) and persists a health data record, using the passed in prototype object as the base. This
     * method is generally used by worker apps as part of upload unpacking.
     *
     * @param record
     *         the prototype record to create from and persist, must be non-null and have valid fields
     * @return the record ID of the persisted record
     */
    public String createOrUpdateRecord(HealthDataRecord record) {
        // validate record
        if (record == null) {
            throw new InvalidEntityException(String.format(Validate.CANNOT_BE_NULL, "HealthDataRecord"));
        }
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);

        // TODO: validate health code, schema ID against DDB tables

        // call through to DAO
        return healthDataDao.createOrUpdateRecord(record);
    }

    /**
     * Deletes all records for the given health code (which corresponds to a user in a particular study). This method
     * is used by admin accounts to delete user health data, or by the user admin service to delete health data when a
     * deleting a user account
     *
     * @param healthCode
     *         health code to delete records for, must be non-null and non-empty
     * @return number of records deleted
     */
    public int deleteRecordsForHealthCode(String healthCode) {
        // validate health code
        if (StringUtils.isBlank(healthCode)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "healthCode"));
        }
        // TODO: validate health code against health code table

        // call through to DAO
        return healthDataDao.deleteRecordsForHealthCode(healthCode);
    }

    /**
     * Gets the health data record using the record ID.
     *
     * @param id
     *         record ID
     * @return health data record
     */
    public HealthDataRecord getRecordById(String id) {
        // validate ID
        if (StringUtils.isBlank(id)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "id"));
        }

        // call through to DAO
        return healthDataDao.getRecordById(id);
    }

    /**
     * Returns a list of all health data records with the given upload date. THis method is generally called by
     * worker apps as part of data export.
     *
     * @param uploadDate
     *         an upload date in YYYY-MM-DD format, must be non-null, non-empty, and represent a valid date
     * @return list of health data records
     */
    public List<HealthDataRecord> getRecordsForUploadDate(String uploadDate) {
        // validate upload date
        if (StringUtils.isBlank(uploadDate)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "uploadDate"));
        }

        // use Joda to parse the upload date, to validate it
        // The DAO takes a string, so we don't ever actually need the LocalDate instance.
        try {
            DateUtils.parseCalendarDate(uploadDate);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(String.format("Expected date format YYYY-MM-DD, received %s", uploadDate));
        }

        // call through to DAO
        return healthDataDao.getRecordsForUploadDate(uploadDate);
    }

    /* HEALTH DATA ATTACHMENT APIs */

    /**
     * Creates or updates a health data attachment. If the specified attachment has no ID, this is consider a new
     * attachment will be created. If the specified attachment does have an ID, this is considered updating an existing
     * attachment.
     *
     * @param attachment
     *         health data attachment to create or update
     * @return attachment ID of the created or updated attachment
     */
    public String createOrUpdateAttachment(HealthDataAttachment attachment) {
        // validate attachment
        if (attachment == null) {
            throw new InvalidEntityException(String.format(Validate.CANNOT_BE_NULL, "HealthDataAttachment"));
        }
        // TODO: validate fields for non-null-ness and non-emptiness
        // TODO: validate record ID against records

        // call through to DAO
        return healthDataAttachmentDao.createOrUpdateAttachment(attachment);
    }

    /* BUILDERS */

    /** Returns a builder object, used for building attachments, for create or update. */
    public HealthDataAttachmentBuilder getAttachmentBuilder() {
        return healthDataAttachmentDao.getRecordBuilder();
    }

    /** Returns a builder object, used for building records, for create or update. */
    public HealthDataRecordBuilder getRecordBuilder() {
        return healthDataDao.getRecordBuilder();
    }

    /**
     * returns received list of record Ids after updating
     * @param recordExportStatusRequest
     *         POJO contains: a lit of health record ids, not upload ids and
     *         an Synapse Exporter Status with value either NOT_EXPORTED or SUCCEEDED
     * @return updated health record ids list
     */
    public List<String> updateRecordsWithExporterStatus(RecordExportStatusRequest recordExportStatusRequest) {
        Validate.entityThrowingException(exporterStatusValidator, recordExportStatusRequest);

        List<String> healthRecordIds = recordExportStatusRequest.getRecordIds();
        HealthDataRecord.ExporterStatus synapseExporterStatus = recordExportStatusRequest.getSynapseExporterStatus();

        if (healthRecordIds.size() > MAX_NUM_RECORD_IDS) {
            throw new BadRequestException("Size of the record ids list exceeds the limit.");
        }

        List<String> updatedRecordIds = healthRecordIds.stream().map(id->{
            DynamoHealthDataRecord record = (DynamoHealthDataRecord) getRecordById(id);
            if (record == null) {
                throw new NotFoundException("The record: " + id + " cannot be found in our database.");
            }
            record.setSynapseExporterStatus(synapseExporterStatus);
            return createOrUpdateRecord(record);
        }).collect(Collectors.toList());

        return updatedRecordIds;
    }
}
