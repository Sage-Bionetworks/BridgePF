package org.sagebionetworks.bridge.dao;

public interface UserLockDao {

    /**
     * Sets a data lock on a target user, where the unique identifier is their
     * email address.
     * 
     * @param healthDataCode
     *            The target user's health data code.
     */
    public void createLock(String healthDataCode);

    /**
     * Releases a data lock on a target user, where the unique identifier is
     * their email address.
     * 
     * @param userEmail
     *            The target user's health data code.
     */
    public void releaseLock(String healthDataCode);

    /**
     * Returns a boolean true if there is a lock, and false otherwise.
     * 
     * @param healthDataCode
     *            The target user's health data code.
     * @return boolean
     */
    public boolean isLocked(String healthDataCode);
}
