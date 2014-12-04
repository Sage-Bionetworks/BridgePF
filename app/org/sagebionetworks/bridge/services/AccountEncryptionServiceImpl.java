package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.studies.Study;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.directory.CustomData;

public class AccountEncryptionServiceImpl implements AccountEncryptionService {

    private static final int CURRENT_VERSION = 2;
    private BridgeEncryptor healthCodeEncryptorOld;
    private AesGcmEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;

    public void setHealthCodeEncryptorOld(BridgeEncryptor encryptor) {
        this.healthCodeEncryptorOld = encryptor;
    }

    public void setHealthCodeEncryptor(AesGcmEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }

    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }

    @Override
    public HealthId createAndSaveHealthCode(Study study, Account account) {
        checkNotNull(study);
        checkNotNull(account);
        final CustomData customData = account.getCustomData();
        final HealthId healthId = healthCodeService.create();
        final String encryptedHealthId = healthCodeEncryptor.encrypt(healthId.getId());
        customData.put(getHealthIdKey(study), encryptedHealthId);
        customData.put(getVersionKey(study), CURRENT_VERSION);
        customData.save();
        return healthId;
    }

    @Override
    public HealthId getHealthCode(Study study, Account account) {
        checkNotNull(study);
        checkNotNull(account);
        CustomData customData = account.getCustomData();
        Object healthIdObj = customData.get(getHealthIdKey(study));
        Object versionObj = customData.get(getVersionKey(study));
        if (versionObj == null) {
            // TODO: Remove me after backfill
            versionObj = customData.get(BridgeConstants.CUSTOM_DATA_VERSION);
        }
        return (getHealthId(healthIdObj, versionObj));
    }

    private String getHealthIdKey(Study study) {
        return study.getIdentifier() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
    }

    private String getVersionKey(Study study) {
        return study.getIdentifier() + BridgeConstants.CUSTOM_DATA_VERSION;
    }

    private HealthId getHealthId(final Object healthIdObj, final Object versionObj) {
        if (healthIdObj == null) {
            return null;
        }
        final int version = versionObj == null ? 1 : (Integer)versionObj;
        String hid = null;
        try {
            hid = version == CURRENT_VERSION ?
                    healthCodeEncryptor.decrypt((String) healthIdObj) :
                    healthCodeEncryptorOld.decrypt((String) healthIdObj);
        } catch(Exception e) {
            // TODO: Remove me
            hid = healthCodeEncryptorOld.decrypt((String) healthIdObj);
        }
        if (hid == null) {
            return null;
        }
        final String healthId = hid;
        final String healthCode = healthCodeService.getHealthCode(healthId);
        return new HealthId() {
            @Override
            public String getId() {
                return healthId;
            }
            @Override
            public String getCode() {
                return healthCode;
            }
        };
    }
}
