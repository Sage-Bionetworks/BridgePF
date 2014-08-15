package org.sagebionetworks.bridge.dao;

public interface UserLockDao {

    /**
     * Sets a data lock on a target user, where the unique identifier is their
     * stormpath ID.
     * 
     * @param stormpathID
     *            The target user's health data code.
     * @return String A UUID used to identify the lock owner.
     */
    public String createLock(String stormpathID);

    /**
     * Releases a data lock on a target user, where the unique identifier is
     * their stormpathID.
     * 
     * @param stormpathID
     *            The target user's stormpath ID.
     * @param UUID
     *            The UUID identifying the lock owner.
     */
    public void releaseLock(String stormpathID, String UUID);

    /**
     * Returns a boolean true if there is a lock, and false otherwise.
     * 
     * @param stormpathID
     *            The target user's stormpath ID.
     * @return boolean
     */
    public boolean isLocked(String stormpathID);
}
