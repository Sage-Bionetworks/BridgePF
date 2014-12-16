package org.sagebionetworks.bridge.dao;

import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.group.Group;

public interface DirectoryDao {

    public String createDirectoryForStudy(String identifier);

    public Directory getDirectoryForStudy(String identifier);

    public void deleteDirectoryForStudy(String identifier);

    public Group getGroup(String name);

}
