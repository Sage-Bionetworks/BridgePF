package org.sagebionetworks.bridge.services.backfill;

import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.TaskEventService;
import org.sagebionetworks.bridge.stormpath.StormpathAccountIterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Maps;
import com.stormpath.sdk.api.ApiKey;
import com.stormpath.sdk.api.ApiKeys;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.client.ClientBuilder;
import com.stormpath.sdk.client.Clients;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.impl.client.DefaultClientBuilder;

@Component("enrollmentEventBackfill")
public class EnrollmentEventBackfill extends AsyncBackfillTemplate {
    
    private BackfillRecordFactory backfillFactory;
    private TaskEventService taskEventService;
    private StudyService studyService;
    private UserConsentDao userConsentDao;
    private HealthCodeService healthCodeService;
    private SortedMap<Integer,Encryptor> encryptors = Maps.newTreeMap();
    
    @Autowired
    public void setBackfillFactory(BackfillRecordFactory backfillFactory) {
        this.backfillFactory = backfillFactory;
    }
    @Autowired
    public void setTaskEventService(TaskEventService taskEventService) {
        this.taskEventService = taskEventService;
    }
    @Autowired
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    @Autowired
    public void setUserConsentDao(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }
    @Autowired
    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }
    @Resource(name="encryptorList")
    public void setEncryptors(List<Encryptor> list) {
        for (Encryptor encryptor : list) {
            encryptors.put(encryptor.getVersion(), encryptor);
        }
    }

    @Override
    int getLockExpireInSeconds() {
        return 30 * 60;
    }

    @Override
    void doBackfill(BackfillTask task, BackfillCallback callback) {
        callback.newRecords(backfillFactory.createOnly(task, "Starting to examine accounts"));
        
        ApiKey apiKey = ApiKeys.builder()
            .setId(BridgeConfigFactory.getConfig().getStormpathId())
            .setSecret(BridgeConfigFactory.getConfig().getStormpathSecret()).build();
        
        ClientBuilder clientBuilder = Clients.builder().setApiKey(apiKey);
        ((DefaultClientBuilder)clientBuilder).setBaseUrl("https://enterprise.stormpath.io/v1");
        
        Client client = clientBuilder.build();
        
        callback.newRecords(backfillFactory.createOnly(task, "Created client"));
        
        //Iterator<Account> combinedIterator = null;
        for (Study study : studyService.getStudies()) {
            callback.newRecords(backfillFactory.createOnly(task, "Getting accounts for study " + study.getName()));
            
            Directory directory = client.getResource(study.getStormpathHref(), Directory.class);
            
            Iterator<Account> studyIterator = new StormpathAccountIterator(study, encryptors, directory.getAccounts().iterator());
            
            while(studyIterator.hasNext()) {
                Account account = studyIterator.next();
                callback.newRecords(backfillFactory.createOnly(task, "Examining account: " + account.getEmail()));
                HealthId mapping = healthCodeService.getMapping(account.getHealthId());
                UserConsent consent = null;
                if (mapping != null) {
                    String healthCode = mapping.getCode();

                    consent = userConsentDao.getUserConsent(healthCode, study.getStudyIdentifier());
                    if (consent != null) {
                        taskEventService.publishEvent(healthCode, consent);
                        callback.newRecords(
                            backfillFactory.createAndSave(task, study, account, "enrollment event created"));
                    }
                }
                if (mapping == null && consent == null) {
                    callback.newRecords(backfillFactory.createOnly(task, "Health code and consent record not found"));
                } else if (mapping == null) {
                    callback.newRecords(backfillFactory.createOnly(task, "Health code not found"));    
                } else if (consent == null) {
                    callback.newRecords(backfillFactory.createOnly(task, "Consent record not found"));
                }
            }
            
            /*
            if (combinedIterator ==  null) {
                combinedIterator = studyIterator;
            } else {
                combinedIterator = Iterators.concat(combinedIterator, studyIterator);
            }
            callback.newRecords(backfillFactory.createOnly(task, "Finished getting accounts for study " + study.getName()));
            */
        }
/*
        while(combinedIterator.hasNext()) {
            Account account = combinedIterator.next();
            callback.newRecords(backfillFactory.createOnly(task, "Examining account: " + account.getEmail()));
            Study study = studyService.getStudy(account.getStudyIdentifier());
            HealthId mapping = healthCodeService.getMapping(account.getHealthId());
            UserConsent consent = null;
            if (mapping != null) {
                String healthCode = mapping.getCode();
                
                consent = userConsentDao.getUserConsent(healthCode, study.getStudyIdentifier());
                if (consent != null) {
                    taskEventService.publishEvent(healthCode, consent);
                    callback.newRecords(
                        backfillFactory.createAndSave(task, study, account, "enrollment event created"));
                }
            }
            if (mapping == null && consent == null) {
                callback.newRecords(backfillFactory.createOnly(task, "Health code and consent record not found"));
            } else if (mapping == null) {
                callback.newRecords(backfillFactory.createOnly(task, "Health code not found"));    
            } else if (consent == null) {
                callback.newRecords(backfillFactory.createOnly(task, "Consent record not found"));
            }
        }
        */
    }
}
