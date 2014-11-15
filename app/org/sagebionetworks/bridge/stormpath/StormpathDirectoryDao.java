package org.sagebionetworks.bridge.stormpath;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.validators.Validate;

import com.stormpath.sdk.application.AccountStoreMapping;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.application.ApplicationCriteria;
import com.stormpath.sdk.application.ApplicationList;
import com.stormpath.sdk.application.Applications;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directories;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.directory.DirectoryCriteria;
import com.stormpath.sdk.directory.DirectoryList;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.group.GroupCriteria;
import com.stormpath.sdk.group.GroupList;
import com.stormpath.sdk.group.Groups;

public class StormpathDirectoryDao implements DirectoryDao {

    private Client client;
    
    public void setStormpathClient(Client client) {
        this.client = client;
    }

    @Override
    public String createDirectory(Environment env, String identifier) {
        checkNotNull(env);
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        Application app = getApplication(env);
        checkNotNull(app);
        String dirName = String.format("%s (%s)", identifier, env.name().toLowerCase());
        String groupName = identifier + "_researcher";

        Directory directory = getDirectory(dirName);
        if (directory == null) {
            directory = client.instantiate(Directory.class);
            directory.setName(dirName);
            directory = client.createDirectory(directory);
        }
        
        AccountStoreMapping mapping = getApplicationMapping(app, directory.getHref());
        if (mapping == null) {
            mapping = client.instantiate(AccountStoreMapping.class);
            mapping.setAccountStore(directory);
            mapping.setApplication(app);
            mapping.setDefaultAccountStore(Boolean.FALSE);
            mapping.setDefaultGroupStore(Boolean.FALSE);
            mapping.setListIndex(10); // this is a priority number
            app.createAccountStoreMapping(mapping);
        }
        
        Group group = getResearcherGroup(app, groupName);
        if (group == null) {
            group = client.instantiate(Group.class);
            group.setName(groupName);
            directory.createGroup(group);
        }
        
        return directory.getHref();
    }

    /* Skip this.
    @Overrid
    public void renameStudyIdentifier(Environment env, String oldIdentifier, String newIdentifier) {
        checkNotNull(env);
        checkArgument(isNotBlank(oldIdentifier), Validate.CANNOT_BE_BLANK, "oldIdentifier");
        checkArgument(isNotBlank(newIdentifier), Validate.CANNOT_BE_BLANK, "newIdentifier");
        Application app = getApplication(env);
        checkNotNull(app);
        
        String oldDirName = String.format("%s (%s)", oldIdentifier, env.name().toLowerCase());
        String newDirName = String.format("%s (%s)", newIdentifier, env.name().toLowerCase());
        
        Directory directory = getDirectory(oldDirName);
        if (directory != null) {
            directory.setName(newDirName);
            directory.save();

            // Also change the name of the researcher group, of course.
            Group group = getResearcherGroup(app, oldIdentifier+"_researcher");
            group.setName(newIdentifier+"_researcher");
            group.save();
        }
    }; */
    
    @Override
    public void deleteDirectory(Environment env, String directoryHref) {
        checkNotNull(env);
        checkArgument(isNotBlank(directoryHref), Validate.CANNOT_BE_BLANK, "directoryHref");
        Application app = getApplication(env);
        checkNotNull(app);
        
        // delete the mapping
        AccountStoreMapping mapping = getApplicationMapping(app, directoryHref);
        if (mapping != null) {
            mapping.delete();
        }
        
        // delete the directory
        Directory directory = client.getResource(directoryHref, Directory.class);
        if (directory != null) {
            directory.delete();    
        }
    }
    
    private Application getApplication(Environment env) {
        String appName = "bridge-"+env.name().toLowerCase();
        ApplicationCriteria criteria = Applications.where(Applications.name().eqIgnoreCase(appName));
        ApplicationList list = client.getApplications(criteria);
        return (list.iterator().hasNext()) ? list.iterator().next() : null;
    }
    
    private AccountStoreMapping getApplicationMapping(Application app, String href) {
        for (AccountStoreMapping mapping : app.getAccountStoreMappings()) {
            if (mapping.getAccountStore().getHref().equals(href)) {
                return mapping;
            }
        }
        return null;
    }
    
    private Directory getDirectory(String name) {
        DirectoryCriteria criteria = Directories.where(Directories.name().eqIgnoreCase(name));
        DirectoryList list = client.getDirectories(criteria);
        return (list.iterator().hasNext()) ? list.iterator().next() : null;
    }
    
    private Group getResearcherGroup(Application app, String name) {
        GroupCriteria criteria = Groups.where(Groups.name().eqIgnoreCase(name));
        GroupList list = app.getGroups(criteria);
        return (list.iterator().hasNext()) ? list.iterator().next() : null;
    }
}
