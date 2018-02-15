package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;
import static org.sagebionetworks.bridge.dao.ParticipantOption.TIME_ZONE;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Maps;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;


@Component
public class ParticipantOptionsService {
    
    private AccountDao accountDao;
    
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    /**
     * Get all options and their values for a participant in a lookup object with type-safe 
     * accessors. If a value is not set, the value will be null in the map. A lookup object 
     * will be returned whether any values have been set for this participant or not. 
     */
    public ParticipantOptionsLookup getOptions(StudyIdentifier studyId, String healthCode) {
        checkArgument(isNotBlank(healthCode));
        
        AccountId accountId = AccountId.forHealthCode(studyId.getIdentifier(), healthCode);
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            Map<String,String> map = Maps.newHashMap();
            add(map, account, ParticipantOption.TIME_ZONE);
            add(map, account, ParticipantOption.SHARING_SCOPE);
            add(map, account, ParticipantOption.EMAIL_NOTIFICATIONS);
            add(map, account, ParticipantOption.EXTERNAL_IDENTIFIER);
            add(map, account, ParticipantOption.DATA_GROUPS);
            add(map, account, ParticipantOption.LANGUAGES);
            return new ParticipantOptionsLookup(map);
        }
        return new ParticipantOptionsLookup(Maps.newHashMap());
    }
    
    private void add(Map<String,String> map, Account account, ParticipantOption option) {
        map.put(option.name(), option.fromAccount(account));
    }
    
    /**
     * Persist a boolean participant option.
     */
    public void setBoolean(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, boolean value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option == ParticipantOption.EMAIL_NOTIFICATIONS);
        
        AccountId accountId = AccountId.forHealthCode(studyIdentifier.getIdentifier(), healthCode);
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            account.setNotifyByEmail(value);
            accountDao.updateAccount(account, false);
        }
    }

    /**
     * Persist a string participant option.
     */
    public void setString(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, String value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option == ParticipantOption.EXTERNAL_IDENTIFIER);
        
        AccountId accountId = AccountId.forHealthCode(studyIdentifier.getIdentifier(), healthCode);
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            account.setExternalId(value);
            accountDao.updateAccount(account, false);
        }
    }

    /**
     * Persist an enumerated participant option.
     */
    public void setEnum(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, Enum<?> value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option == ParticipantOption.SHARING_SCOPE);
        
        AccountId accountId = AccountId.forHealthCode(studyIdentifier.getIdentifier(), healthCode);
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            account.setSharingScope((SharingScope)value);
            accountDao.updateAccount(account, false);
        }
    }

    /**
     * Persist a string set option. The keys in the string set are persisted in the order they are retrieved from a set, 
     * and returned in that same order.
     */
    public void setStringSet(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, Set<String> value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option == ParticipantOption.DATA_GROUPS);
        
        AccountId accountId = AccountId.forHealthCode(studyIdentifier.getIdentifier(), healthCode);
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            account.setDataGroups(value);
            accountDao.updateAccount(account, false);
        }
    }

    /**
     * Persist a string set option with a set of keys that are ordered by their insertion in the set.
     */
    public void setOrderedStringSet(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, LinkedHashSet<String> value) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option == ParticipantOption.LANGUAGES);
        
        AccountId accountId = AccountId.forHealthCode(studyIdentifier.getIdentifier(), healthCode);
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            account.setLanguages(value);
            accountDao.updateAccount(account, false);
        }
    }
    
    public void setDateTimeZone(StudyIdentifier studyIdentifier, String healthCode, ParticipantOption option, DateTimeZone zone) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(option == ParticipantOption.TIME_ZONE);

        AccountId accountId = AccountId.forHealthCode(studyIdentifier.getIdentifier(), healthCode);
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            account.setTimeZone(zone);
            accountDao.updateAccount(account, false);
        }
    }
    
    public void setAllOptions(StudyIdentifier studyIdentifier, String healthCode, Map<ParticipantOption,String> options) {
        checkNotNull(studyIdentifier);
        checkArgument(isNotBlank(healthCode));
        checkNotNull(options);

        AccountId accountId = AccountId.forHealthCode(studyIdentifier.getIdentifier(), healthCode);
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            // It's easiest to convert this to a lookup object to do conversions.
            Map<String,String> map = Maps.newHashMap();
            options.entrySet().stream().forEach((entry) -> {
                map.put(entry.getKey().name(), entry.getValue());
            });
            ParticipantOptionsLookup lookup = new ParticipantOptionsLookup(map);
            account.setTimeZone(lookup.getTimeZone(TIME_ZONE));
            account.setSharingScope(lookup.getEnum(SHARING_SCOPE, SharingScope.class));
            account.setNotifyByEmail(lookup.getBoolean(EMAIL_NOTIFICATIONS));
            account.setExternalId(lookup.getString(EXTERNAL_IDENTIFIER));
            account.setDataGroups(lookup.getStringSet(DATA_GROUPS));
            account.setLanguages(lookup.getOrderedStringSet(LANGUAGES));
            accountDao.updateAccount(account, false);
        }
    }
}
