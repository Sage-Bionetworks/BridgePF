package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

public interface Account extends BridgeEntity {

    public String getUsername();
    public void setUsername(String username);
    
    public String getFirstName();
    public void setFirstName(String firstName);
    
    public String getLastName();
    public void setLastName(String lastName);
    
    public String getPhone();
    public void setPhone(String phone);
    
    public String getEmail();
    public void setEmail(String email);
    
    public String getHealthCode();
    
}
