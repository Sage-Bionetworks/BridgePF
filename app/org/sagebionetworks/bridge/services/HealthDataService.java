package org.sagebionetworks.bridge.services;

import java.util.List;

import com.google.common.base.Strings;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.validators.HealthDataRecordValidator;
import org.sagebionetworks.bridge.validators.Validate;

/** Service handler for health data APIs. */
@Component
public class HealthDataService {
    private HealthDataDao healthDataDao;

    /** Health data DAO. This is configured by Spring. */
    @Autowired
    public void setHealthDataDao(HealthDataDao healthDataDao) {
        this.healthDataDao = healthDataDao;
    }

    /**
     * Creates and persists a health data record, using the passed in prototype object as the base. This method is
     * generally used by worker apps as part of upload unpacking.
     *
     * @param record
     *         the prototype record to create from and persist, must be non-null and have valid fields
     * @return the record ID of the persisted record
     */
    public String createRecord(HealthDataRecord record) {
        // validate record
        if (record == null) {
            throw new InvalidEntityException(String.format(Validate.CANNOT_BE_NULL, "HealthDataRecord"));
        }
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);

        // call through to DAO
        return healthDataDao.createRecord(record);
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
        if (Strings.isNullOrEmpty(uploadDate)) {
            throw new BadRequestException(String.format(Validate.CANNOT_BE_BLANK, "uploadDate"));
        }

        // use Joda to parse the upload date, to validate it
        // The DAO takes a string, so we don't ever actually need the LocalDate instance.
        try {
            LocalDate.parse(uploadDate, ISODateTimeFormat.date());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(String.format("Expected date format YYYY-MM-DD, received %s", uploadDate));
        }

        // call through to DAO
        return healthDataDao.getRecordsForUploadDate(uploadDate);
    }
}
