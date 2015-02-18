package org.sagebionetworks.bridge.dao;

import javax.annotation.Nonnull;
import java.util.List;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;

/** DAO for health data records. */
public interface HealthDataDao {
    /**
     * DAO method used by worker apps to creating a health data record and persisting it, generally from unpacking
     * uploads.
     *
     * @param record
     *         health data record prototype, from which the record should be created from, must be non-null
     * @return the unique ID of the created record
     */
    String createRecord(@Nonnull HealthDataRecord record);

    /**
     * DAO method used by worker apps to query all health data records uploaded for a specific date, generally used for
     * export.
     *
     * @param uploadDate
     *         upload date in YYYY-MM-DD format, must be non-null, non-empty, and must represent a valid date
     * @return list of all health records uploaded on that date
     */
    List<HealthDataRecord> getRecordsForUploadDate(@Nonnull String uploadDate);

    /**
     * Gets a builder instance, used for building prototype health data records. This is generally used by worker apps
     * to unpack uploads.
     */
    HealthDataRecordBuilder getRecordBuilder();
}
