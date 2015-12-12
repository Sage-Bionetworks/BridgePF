package org.sagebionetworks.bridge.dao;

import java.util.List;
import java.util.Set;

import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

public interface UserConsentDao {

    /**
     * Gives consent to the specified study.
     * @param healthCode
     * @param subpopGuid
     * @param consentCreatedOn
     * @param signedOn
     * @return
     *      the consent record
     */
    UserConsent giveConsent(String healthCode, SubpopulationGuid subpopGuid, long consentCreatedOn, long signedOn);

    /**
     * Withdraws consent to the specified study.
     * @param healthCode
     * @param subpopGuid
     * @param withdrewOn
     * @return
     */
    void withdrawConsent(String healthCode, SubpopulationGuid subpopGuid, long withdrewOn);

    /**
     * Whether the user has consented to the specified study.
     * @param healthCode
     * @param subpopGuid
     * @return
     */
    boolean hasConsented(String healthCode, SubpopulationGuid subpopGuid);

    /**
     * Get the active user consent record (a consent that has not been withdrawn), 
     * that consents the user to this study. This method returns null if there is no 
     * active consent (the user has consented to this subpopulation).
     * @param healthCode
     * @param subpopGuid
     * @return
     */
    UserConsent getActiveUserConsent(String healthCode, SubpopulationGuid subpopGuid);

    /**
     * Get a specific consent signature by the signing date. These are retrieved when reconstructing 
     * the users consent history.
     * @param healthCode
     * @param subpopGuid
     * @param signedOn
     * @return
     */
    UserConsent getUserConsent(String healthCode, SubpopulationGuid subpopGuid, long signedOn);
    
    /**
     * Get the entire history of user consent records (including withdrawn consents, if any).
     * @param healthCode
     * @param subpopGuid
     * @return
     */
    List<UserConsent> getUserConsentHistory(String healthCode, SubpopulationGuid subpopGuid);
    
    /**
     * Delete all consent records for a user, in order to clean up after tests. If withdrawing a user, 
     * call <code>withDrawConsent()</code> instead.
     */
    void deleteAllConsents(String healthCode, SubpopulationGuid subpopGuid);
    
    /**
     * Get a set of unique health codes that have consented to this subpopulation's consent. Used to 
     * calculate the number of participants in a study.
     * @param subpopGuid
     * @return
     */
    Set<String> getParticipantHealthCodes(SubpopulationGuid subpopGuid);
}
