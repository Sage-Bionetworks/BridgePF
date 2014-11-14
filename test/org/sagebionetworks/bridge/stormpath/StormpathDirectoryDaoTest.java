package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.config.Environment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.stormpath.sdk.application.AccountStoreMapping;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.application.ApplicationCriteria;
import com.stormpath.sdk.application.ApplicationList;
import com.stormpath.sdk.application.Applications;
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
    
    @Test
    public void crudDirectory() {
        String name = RandomStringUtils.randomAlphabetic(5);
        String href = directoryDao.createDirectory(Environment.LOCAL, name);
        
        // Verify the directory and mapping were created
        Directory directory = getDirectory(href);
        assertEquals("Name is the right one", name + " (local)", directory.getName());
        assertTrue("Mapping exists for new directory in the right application", containsMapping(href));
        assertTrue("The researcher group was created", researcherGroupExists(directory, name));
        
        directoryDao.deleteDirectory(Environment.LOCAL, href);
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
    
    private Application applicationForEnvironment(Environment env) {
        String appName = "bridge-"+env.name().toLowerCase();
        ApplicationCriteria criteria = Applications.where(Applications.name().eqIgnoreCase(appName));
        ApplicationList list = client.getApplications(criteria);
        return (list.iterator().hasNext()) ? list.iterator().next() : null;
    }
    
    private boolean containsMapping(String href) {
        Application app = applicationForEnvironment(Environment.LOCAL);
        for (AccountStoreMapping mapping : app.getAccountStoreMappings()) {
            if (mapping.getAccountStore().getHref().equals(href)) {
                return true;    
            }
        }
        return false;
    }
    
}
