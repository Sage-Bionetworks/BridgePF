package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.TEST_USERS;

import javax.annotation.Resource;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.EmailTemplate.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
    
    private DynamoStudy study;
    
    @After
    public void after() {
        if (study != null) {
            directoryDao.deleteDirectoryForStudy(study);
        }
    }
    
    @Test
    public void crudDirectory() throws Exception {
        String identifier = TestUtils.randomName();
        
        study = TestUtils.getValidStudy();
        study.setIdentifier(identifier);
        
        String stormpathHref = directoryDao.createDirectoryForStudy(study);
        study.setStormpathHref(stormpathHref);
        BridgeConfig config = BridgeConfigFactory.getConfig();
        
        // Verify the directory and mapping were created
        Directory directory = getDirectory(stormpathHref);

        assertEquals("Name is the right one", identifier + " ("+config.getEnvironment().name().toLowerCase()+")", directory.getName());
        assertTrue("Mapping exists for new directory in the right application", containsMapping(stormpathHref));
        assertTrue("The researcher group was created", groupExists(directory, RESEARCHER));
        assertTrue("The developer group was created", groupExists(directory, DEVELOPER));
        assertTrue("The admin group was created", groupExists(directory, ADMIN));
        assertTrue("The test_users group was created", groupExists(directory, TEST_USERS));
        
        Directory newDirectory = directoryDao.getDirectoryForStudy(study);
        assertDirectoriesAreEqual(study, "subject", "subject", directory, newDirectory);
        
        // Verify that we can update the directory.
        study.setPasswordPolicy(new PasswordPolicy(3, true, true, true, true));
        study.setResetPasswordTemplate(new EmailTemplate("new rp subject", "new rp body ${url}", MimeType.TEXT));
        study.setVerifyEmailTemplate(new EmailTemplate("new ve subject", "<p>new ve body ${url}</p>", MimeType.HTML));
        
        directoryDao.updateDirectoryForStudy(study);
        
        newDirectory = directoryDao.getDirectoryForStudy(study);
        assertDirectoriesAreEqual(study, "new rp subject", "new ve subject", directory, newDirectory);
        
        directoryDao.deleteDirectoryForStudy(study);
        newDirectory = directoryDao.getDirectoryForStudy(study);
        assertNull("Directory has been deleted", newDirectory);
        
        study = null;
    }

    private void assertDirectoriesAreEqual(DynamoStudy study, String rpSubject, String veSubject, Directory directory, Directory newDirectory) throws Exception {
        assertEquals(directory.getHref(), newDirectory.getHref());
        
        com.stormpath.sdk.directory.PasswordPolicy passwordPolicy = newDirectory.getPasswordPolicy();
        assertEquals(EmailStatus.ENABLED, passwordPolicy.getResetEmailStatus());
        assertEquals(EmailStatus.DISABLED, passwordPolicy.getResetSuccessEmailStatus());
        assertEquals(1, passwordPolicy.getResetEmailTemplates().getSize());
        
        // Reset Password Template
        ModeledEmailTemplate template = passwordPolicy.getResetEmailTemplates().iterator().next();
        assertEquals(study.getSponsorName(), template.getFromName());
        assertEquals(study.getSupportEmail(), template.getFromEmailAddress());
        assertEquals(rpSubject, template.getSubject());
        assertEquals(com.stormpath.sdk.mail.MimeType.PLAIN_TEXT, template.getMimeType());
        assertEquals(study.getResetPasswordTemplate().getBody(), template.getTextBody());
        String url = String.format("%s/mobile/resetPassword.html?study=%s", BridgeConfigFactory.getConfig().getBaseURL(), study.getIdentifier());
        assertEquals(url, template.getLinkBaseUrl());
        
        PasswordStrength strength = passwordPolicy.getStrength();
        assertEquals(PasswordPolicy.FIXED_MAX_LENGTH, strength.getMaxLength());
        assertEquals(study.getPasswordPolicy().isNumericRequired() ? 1 : 0, strength.getMinNumeric());
        assertEquals(study.getPasswordPolicy().isSymbolRequired() ? 1 : 0, strength.getMinSymbol());
        assertEquals(study.getPasswordPolicy().isLowerCaseRequired() ? 1 : 0, strength.getMinLowerCase());
        assertEquals(study.getPasswordPolicy().isUpperCaseRequired() ? 1 : 0, strength.getMinUpperCase());
        assertEquals(0, strength.getMinDiacritic());
        assertEquals(study.getPasswordPolicy().getMinLength(), strength.getMinLength());
        
        // Need to use HTTP and the REST API to retrieve and test the verify email template...
        EmailTemplate ve = study.getVerifyEmailTemplate();
        BridgeConfig config = BridgeConfigFactory.getConfig();
        
        // Create an HTTP client using Basic Auth with stormpath credentials (yes it's basic auth but it's over SSL and going away)
        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(config.getStormpathId(), config.getStormpathSecret());
        provider.setCredentials(AuthScope.ANY, credentials);
        CloseableHttpClient client = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
        
        // Get directory as JSON
        ObjectNode directoryNode = StormpathUtils.getJSON(client, directory.getHref());
        String accountCreationUrl = directoryNode.get("accountCreationPolicy").get("href").asText();
        
        // Get account policy as JSON, update to our standard configuration
        ObjectNode accountPolicyNode = StormpathUtils.getJSON(client, accountCreationUrl);
        String verificationEmailTemplatesUrl = accountPolicyNode.get("verificationEmailTemplates").get("href").asText();
        
        // Get the verify email template
        ObjectNode templateNode = StormpathUtils.getJSON(client, verificationEmailTemplatesUrl);
        String templateUrl = templateNode.get("items").get(0).get("href").asText();
        // Update this template with study-specific information
        ObjectNode templateJSON = StormpathUtils.getJSON(client, templateUrl);
        
        // TODO: Test that the study name is used if no study name is provided.
        assertEquals(study.getSponsorName(), templateJSON.get("fromName").asText());
        assertEquals(study.getSupportEmail(), templateJSON.get("fromEmailAddress").asText());
        assertEquals(ve.getSubject(), templateJSON.get("subject").asText());
        assertEquals(ve.getMimeType().toString(), templateJSON.get("mimeType").asText());
        assertEquals(ve.getBody(), templateJSON.get("textBody").asText());
        
        assertTrue(templateJSON.get("defaultModel").get("linkBaseUrl").asText().contains("/mobile/verifyEmail.html?study="));
    }
    
    private boolean groupExists(Directory directory, Roles role) {
        for (Group group : directory.getGroups()) {
            if (group.getName().equals(role.name().toLowerCase())) {
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
