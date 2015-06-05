package org.sagebionetworks.bridge.stormpath;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Map;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Maps;
import com.stormpath.sdk.application.AccountStoreMapping;
import com.stormpath.sdk.application.AccountStoreMappingCriteria;
import com.stormpath.sdk.application.AccountStoreMappings;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directories;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.directory.DirectoryCriteria;
import com.stormpath.sdk.directory.DirectoryList;
import com.stormpath.sdk.directory.PasswordPolicy;
import com.stormpath.sdk.directory.PasswordStrength;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.group.GroupCriteria;
import com.stormpath.sdk.group.GroupList;
import com.stormpath.sdk.group.Groups;
import com.stormpath.sdk.mail.EmailStatus;
import com.stormpath.sdk.mail.MimeType;
import com.stormpath.sdk.mail.ModeledEmailTemplate;
import com.stormpath.sdk.mail.ModeledEmailTemplateList;

@Component
public class StormpathDirectoryDao implements DirectoryDao {

    private static Logger logger = LoggerFactory.getLogger(StormpathDirectoryDao.class);

    private static final AccountStoreMappingCriteria asmCriteria = AccountStoreMappings.criteria().limitTo(100);
    private BridgeConfig config;
    private Client client;

    @Autowired
    public void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.config = bridgeConfig;
    }
    @Autowired
    public void setStormpathClient(Client client) {
        this.client = client;
    }

    @Override
    public String createDirectoryForStudy(Study study) {
        checkNotNull(study);
        checkArgument(isNotBlank(study.getIdentifier()), Validate.CANNOT_BE_BLANK, "identifier");
        Application app = getApplication();
        checkNotNull(app);
        String dirName = createDirectoryName(study.getIdentifier());
        String groupName = createGroupName(study.getIdentifier());

        Directory directory = getDirectory(dirName);
        if (directory == null) {
            directory = client.instantiate(Directory.class);
            directory.setName(dirName);
            directory = client.createDirectory(directory);
        }
        
        adjustPasswordPolicies(study, directory);
        //adjustVerifyEmailPolicies(study, directory);
        
        AccountStoreMapping mapping = getApplicationMapping(directory.getHref(), app);
        if (mapping == null) {
            mapping = client.instantiate(AccountStoreMapping.class);
            mapping.setAccountStore(directory);
            mapping.setApplication(app);
            mapping.setDefaultAccountStore(Boolean.FALSE);
            mapping.setDefaultGroupStore(Boolean.FALSE);
            mapping.setListIndex(10); // this is a priority number
            app.createAccountStoreMapping(mapping);
        }
        
        // NOTE: As these are application scoped, they only work because the researcher role won't already exist.
        // The admin is not created because it is found in another study (API).
        Group group = getGroup(app, groupName);
        if (group == null) {
            group = client.instantiate(Group.class);
            group.setName(groupName);
            directory.createGroup(group);
        }
        group = getGroup(app, BridgeConstants.ADMIN_GROUP);
        if (group == null) {
            group = client.instantiate(Group.class);
            group.setName(BridgeConstants.ADMIN_GROUP);
            directory.createGroup(group);
        }

        return directory.getHref();
    }
    
    @Override
    public void updateDirectoryForStudy(Study study) {
        checkNotNull(study);
        
        // The only thing you can really do here is update the templates, password policy, etc.
        // Assme this call isn't made unless it's determined that some aspect of the study has changed
        // relative to what is in DDB.
        Directory directory = getDirectoryForStudy(study.getIdentifier());
        adjustPasswordPolicies(study, directory);
        //adjustVerifyEmailPolicies(study, directory);
    };

    @Override
    public Directory getDirectoryForStudy(String identifier) {
        String dirName = createDirectoryName(identifier);
        return getDirectory(dirName);
    }

    @Override
    public void deleteDirectoryForStudy(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        Application app = getApplication();
        checkNotNull(app);

        Directory existing = getDirectory(createDirectoryName(identifier));
        
        // delete the mapping
        AccountStoreMapping mapping = getApplicationMapping(existing.getHref(), app);
        if (mapping != null) {
            mapping.delete();
        } else {
            logger.warn("AccountStoreMapping not found: " + app.getName() + ", " + existing.getHref());
        }

        // delete the directory
        Directory directory = client.getResource(existing.getHref(), Directory.class);
        if (directory != null) {
            directory.delete();
        } else {
            logger.warn("Directory not found: " + existing.getHref());
        }
    }

    @Override
    public Group getGroup(String name) {
        Application app = getApplication();
        return getGroup(app, name);
    }

    private Group getGroup(Application app, String name) {
        GroupCriteria criteria = Groups.where(Groups.name().eqIgnoreCase(name));
        GroupList list = app.getGroups(criteria);
        return (list.iterator().hasNext()) ? list.iterator().next() : null;
    }

    private String createGroupName(String identifier) {
        return identifier + "_researcher";
    }

    private String createDirectoryName(String identifier) {
        return String.format("%s (%s)", identifier, config.getEnvironment().name().toLowerCase());
    }

    private Application getApplication() {
        return client.getResource(config.getStormpathApplicationHref(), Application.class);
    }
    
    private AccountStoreMapping getApplicationMapping(String href, Application app) {
        // This is tedious but I see no way to search for or make a reference to this 
        // mapping without iterating through the application's mappings.
        for (AccountStoreMapping mapping : app.getAccountStoreMappings(asmCriteria)) {
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
    
    private void adjustPasswordPolicies(Study study, Directory directory) {
        PasswordPolicy passwordPolicy = directory.getPasswordPolicy();
        passwordPolicy.setResetEmailStatus(EmailStatus.ENABLED);
        passwordPolicy.setResetSuccessEmailStatus(EmailStatus.DISABLED);
        
        ModeledEmailTemplateList resetEmailTemplates = passwordPolicy.getResetEmailTemplates();
        for (ModeledEmailTemplate template : resetEmailTemplates) {
            template.setFromName(study.getSponsorName());
            template.setFromEmailAddress(study.getSupportEmail());
            
            MimeType stormpathMimeType = getStormpathMimeType(study);
            template.setMimeType(stormpathMimeType);
            
            String subject = partiallyResolveTemplate(study.getResetPasswordTemplate().getSubject(), study);
            template.setSubject(subject);

            String body = partiallyResolveTemplate(study.getResetPasswordTemplate().getBody(), study);
            template.setTextBody(body);
            String link = String.format("%s/mobile/resetPassword.html?study=%s", 
                BridgeConfigFactory.getConfig().getBaseURL(), study.getIdentifier());
            template.setLinkBaseUrl(link);
            template.save();
        }
        
        PasswordStrength strength = passwordPolicy.getStrength();
        strength.setMaxLength(100);
        strength.setMinLowerCase(0);
        strength.setMinDiacritic(0);
        strength.setMinLength(study.getPasswordPolicy().getMinLength());
        strength.setMinNumeric(study.getPasswordPolicy().isRequireNumeric() ? 1 : 0);
        strength.setMinSymbol(study.getPasswordPolicy().isRequireSymbol() ? 1 : 0);
        strength.setMinUpperCase(study.getPasswordPolicy().isRequireUpperCase() ? 1 : 0);
        strength.save();
        passwordPolicy.save();
    }
    
    private MimeType getStormpathMimeType(Study study) {
        return (study.getResetPasswordTemplate().getMimeType() == EmailTemplate.MimeType.TEXT) ? 
            MimeType.PLAIN_TEXT : MimeType.HTML;
    }
    
    private String partiallyResolveTemplate(String template, Study study) {
        Map<String,String> map = Maps.newHashMap();
        map.put("studyName", study.getName());
        map.put("supportEmail", study.getSupportEmail());
        map.put("technicalEmail", study.getTechnicalEmail());
        map.put("sponsorName", study.getSponsorName());
        return BridgeUtils.resolveTemplate(template, map);
    }
}
