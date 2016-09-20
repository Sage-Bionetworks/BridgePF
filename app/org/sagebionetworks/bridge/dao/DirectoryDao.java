package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.studies.Study;

import com.stormpath.sdk.directory.Directory;

public interface DirectoryDao {

    String createDirectoryForStudy(Study study);

    void updateDirectoryForStudy(Study study);
    
    Directory getDirectoryForStudy(Study study);

    void deleteDirectoryForStudy(Study study);

}
