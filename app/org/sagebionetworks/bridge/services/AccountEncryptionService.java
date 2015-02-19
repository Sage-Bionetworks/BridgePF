package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface AccountEncryptionService {

    /**
     * Creates a new health ID and save it encrypted in the account.
     */
    HealthId createAndSaveHealthCode(Study study, Account account);

    /**
     * Gets the decrypted health ID associated with the account.
     * Returns null if the account has no health ID.
     */
    HealthId getHealthCode(StudyIdentifier studyIdentifier, Account account);

    /**
     * Encrypts and saves the consent signature. Does both create and update.
     */
    void putConsentSignature(Study study, Account account, ConsentSignature consentSignature);

    /**
     * Gets the decrypted consent signature. Throws EntityNotFoundException if the signature does not exist.
     */
    ConsentSignature getConsentSignature(Account account);

    /**
     * Removes consent signature.
     */
    void removeConsentSignature(Study study, Account account);
    
}
