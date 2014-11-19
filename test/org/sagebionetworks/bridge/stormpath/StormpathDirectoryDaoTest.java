package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.stormpath.sdk.application.AccountStoreMapping;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.group.Group;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StormpathDirectoryDaoTest {

    @Resource
    StormpathDirectoryDao directoryDao;
    
    @Resource
    Client client;
    
    private String identifier;
    
    @After
    public void after() {
        if (identifier != null) {
            directoryDao.deleteDirectoryForStudy(identifier);
        }
    }
    
    @Test
    public void crudDirectory() {
        identifier = TestUtils.randomName();
        String stormpathHref = directoryDao.createDirectoryForStudy(identifier);
        BridgeConfig config = BridgeConfigFactory.getConfig();
        
        // Verify the directory and mapping were created
        Directory directory = getDirectory(stormpathHref);
        assertEquals("Name is the right one", identifier + " ("+config.getEnvironment().name().toLowerCase()+")", directory.getName());
        assertTrue("Mapping exists for new directory in the right application", containsMapping(stormpathHref));
        assertTrue("The researcher group was created", researcherGroupExists(directory, identifier));
        
        Directory newDirectory = directoryDao.getDirectoryForStudy(identifier);
        assertEquals("Directory is in map", directory.getHref(), newDirectory.getHref());
        
        directoryDao.deleteDirectoryForStudy(identifier);
        
        newDirectory = directoryDao.getDirectoryForStudy(identifier);
        assertNull("Directory has been deleted", newDirectory);
        
        identifier = null;
    }
    
    private boolean researcherGroupExists(Directory directory, String name) {
        for (Group group : directory.getGroups()) {
            if (group.getName().equals(name+"_researcher")) {
                return true;
            }
        }
        return false;
    }
    
    private Directory getDirectory(String href) {
        return client.getResource(href, Directory.class);
    }
    
    private Application getApplication() {
        return client.getResource(BridgeConfigFactory.getConfig().getStormpathApplicationHref(), Application.class);
    }
    
    private boolean containsMapping(String href) {
        Application app = getApplication();
        for (AccountStoreMapping mapping : app.getAccountStoreMappings()) {
            if (mapping.getAccountStore().getHref().equals(href)) {
                return true;    
            }
        }
        return false;
    }
    
}
