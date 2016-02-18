package org.sagebionetworks.bridge.dao;

import org.joda.time.DateTime;

/** DAO to answer the simple question: Given some upload attributes, is this upload a duplicate? */
public interface UploadDedupeDao {
    /**
     * <p>
     * Given these upload attributes, determine if the upload is a duplicate of a pre-existing upload. An upload is a
     * duplicate if has the same healthCode (same user) and Upload MD5 (same data) as a previous upload. Because MD5s
     * can collide, it further limits possible dupes by using the uploadRequestedOn timestamp. If it is a duplicate,
     * return the upload ID. If it's not, return null.
     * </p>
     * <p>
     * Note that this method is read only and has no side effects. If you want to write to UploadDedupe so that future
     * calls recognize the upload as a dupe, call {@link #registerUpload}.
     * </p>
     *
     * @param healthCode
     *         user's health code
     * @param uploadMd5
     *         upload data's MD5
     * @param uploadRequestedOn
     *         time that the upload was requested
     * @return the upload ID of the previous upload, if it's a dupe; null if it's not
     */
    String getDuplicate(String healthCode, String uploadMd5, DateTime uploadRequestedOn);

    /**
     * Writes upload attributes to the DAO so that future calls to {@link #getDuplicate} recognize these attributes as
     * duplicates.
     *
     * @param healthCode
     *         user's health code
     * @param uploadMd5
     *         upload data's MD5
     * @param uploadRequestedOn
     *         time that the upload was requested
     * @param originalUploadId
     *         upload ID to register in the dedupe table
     */
    void registerUpload(String healthCode, String uploadMd5, DateTime uploadRequestedOn, String originalUploadId);
}
