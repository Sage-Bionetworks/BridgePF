package org.sagebionetworks.bridge.models;

public interface Upload {

    String getUploadId();

    long getTimestamp();

    String getObjectId();

    String getHealthCode();

    boolean isComplete();

    String getName();

    long getContentLength();

    String getContentType();

    String getContentMd5();
}
