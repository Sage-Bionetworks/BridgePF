package org.sagebionetworks.bridge.models;

public interface Upload {

    String getUploadId();

    long getTimestamp();

    String getObjectId();

    String getHealthCode();

    boolean isComplete();

    Long getVersion();

    String getName();

    int getContentLength();

    String getContentType();

    String getContentMd5();
}
