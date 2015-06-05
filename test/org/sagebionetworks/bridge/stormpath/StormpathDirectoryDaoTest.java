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
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.EmailTemplate.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.stormpath.sdk.application.AccountStoreMapping;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.directory.PasswordStrength;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.mail.EmailStatus;
import com.stormpath.sdk.mail.ModeledEmailTemplate;

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
        
        DynamoStudy study = TestUtils.getValidStudy();
        study.setIdentifier(identifier);
        
        String stormpathHref = directoryDao.createDirectoryForStudy(study);
        BridgeConfig config = BridgeConfigFactory.getConfig();
        
        // Verify the directory and mapping were created
        Directory directory = getDirectory(stormpathHref);
        
        assertEquals("Name is the right one", identifier + " ("+config.getEnvironment().name().toLowerCase()+")", directory.getName());
        assertTrue("Mapping exists for new directory in the right application", containsMapping(stormpathHref));
        assertTrue("The researcher group was created", researcherGroupExists(directory, identifier));
        
        Directory newDirectory = directoryDao.getDirectoryForStudy(identifier);
        assertDirectoriesAreEqual(study, "subject", directory, newDirectory);
        
        // Verify that we can update the directory.
        study.setPasswordPolicy(new PasswordPolicy(3, false, false, false));
        study.setResetPasswordTemplate(new EmailTemplate("new subject", "new body ${url}", MimeType.TEXT));
        directoryDao.updateDirectoryForStudy(study);
        
        newDirectory = directoryDao.getDirectoryForStudy(identifier);
        assertDirectoriesAreEqual(study, "new subject", directory, newDirectory);
        
        directoryDao.deleteDirectoryForStudy(study.getIdentifier());
        newDirectory = directoryDao.getDirectoryForStudy(identifier);
        assertNull("Directory has been deleted", newDirectory);
        
        identifier = null;
    }

    private void assertDirectoriesAreEqual(DynamoStudy study, String subject, Directory directory, Directory newDirectory) {
        assertEquals(directory.getHref(), newDirectory.getHref());
        
        com.stormpath.sdk.directory.PasswordPolicy passwordPolicy = newDirectory.getPasswordPolicy();
        assertEquals(EmailStatus.ENABLED, passwordPolicy.getResetEmailStatus());
        assertEquals(EmailStatus.DISABLED, passwordPolicy.getResetSuccessEmailStatus());
        assertEquals(1, passwordPolicy.getResetEmailTemplates().getSize());
        ModeledEmailTemplate template = passwordPolicy.getResetEmailTemplates().iterator().next();

        assertEquals(study.getSponsorName(), template.getFromName());
        assertEquals(study.getSupportEmail(), template.getFromEmailAddress());
        assertEquals(subject, template.getSubject());
        assertEquals(com.stormpath.sdk.mail.MimeType.PLAIN_TEXT, template.getMimeType());
        assertEquals(study.getResetPasswordTemplate().getBody(), template.getTextBody());
        String url = String.format("%s/mobile/resetPassword.html?study=%s", BridgeConfigFactory.getConfig().getBaseURL(), study.getIdentifier());
        assertEquals(url, template.getLinkBaseUrl());

        PasswordStrength strength = passwordPolicy.getStrength();
        assertEquals(100, strength.getMaxLength());
        assertEquals(0, strength.getMinLowerCase());
        assertEquals(study.getPasswordPolicy().isRequireNumeric() ? 1 : 0, strength.getMinNumeric());
        assertEquals(study.getPasswordPolicy().isRequireSymbol() ? 1 : 0, strength.getMinSymbol());
        assertEquals(study.getPasswordPolicy().isRequireUpperCase() ? 1 : 0, strength.getMinUpperCase());
        assertEquals(0, strength.getMinDiacritic());
        assertEquals(study.getPasswordPolicy().getMinLength(), strength.getMinLength());
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
