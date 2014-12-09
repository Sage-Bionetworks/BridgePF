package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;

import com.stormpath.sdk.account.Account;

public interface AccountEncryptionService {

    /**
     * Creates a new health ID and save it encrypted in the account.
     */
    HealthId createAndSaveHealthCode(Study study, Account account);

    /**
     * Gets the decrypted health ID associated with the account.
     * Returns null if the account has no health ID.
     */
    HealthId getHealthCode(Study study, Account account);

    /**
     * Encrypts and saves the consent signature.
     */
    void putConsentSignature(Study study, Account account, ConsentSignature consentSignature);

    /**
     * Gets the decrypted consent signature.
     */
    ConsentSignature getConsentSignature(Study study, Account account);

    /**
     * Removes consent signature.
     */
    ConsentSignature removeConsentSignature(Study study, Account account);
}
