package org.sagebionetworks.bridge.exceptions;

import org.apache.http.HttpStatus;
import org.sagebionetworks.bridge.models.ClientInfo;

@SuppressWarnings("serial")
@NoStackTraceException
public class MinSupportedVersionException extends BridgeServiceException {
    
    private final ClientInfo clientInfo;
    
    public MinSupportedVersionException(ClientInfo clientInfo) {
        super("This app version is not supported. Please update.", HttpStatus.SC_GONE);	// 410
        this.clientInfo = clientInfo;
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }
    
}