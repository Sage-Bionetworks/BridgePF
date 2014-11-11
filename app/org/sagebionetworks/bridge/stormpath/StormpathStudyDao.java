package org.sagebionetworks.bridge.stormpath;

import java.util.List;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.DirectoryDao;

import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;

public class StormpathStudyDao implements DirectoryDao {

    private Client client;
    private BridgeConfig config;
    
    public void setStormpathClient(Client client) {
        this.client = client;
    }
    
    public void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }

    @Override
    public String createDirectory(Environment env, String name) {
        String dirName = String.format("%s (%s)", name, env.name().toLowerCase());

        Directory directory = client.instantiate(Directory.class).setName(dirName);
        client.createDirectory(directory);

        // Associate to the relevant application.

        return directory.getHref();
    }
    
    @Override
    public void deleteDirectory(String directoryHref) {
        
    }

}
