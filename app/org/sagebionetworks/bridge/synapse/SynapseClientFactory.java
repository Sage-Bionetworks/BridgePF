package org.sagebionetworks.bridge.synapse;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import org.sagebionetworks.repo.model.DomainType;

public class SynapseClientFactory {

    public static SynapseClient createSynapseClient(BridgeConfig config) {
        if (config.isStub()) {
            return StubSynapseClient.createInstance();            
        } else {
            SynapseClient client = new SynapseClientImpl(DomainType.BRIDGE);
            client.setRepositoryEndpoint(config.getSynapseRepoEndpoint());
            client.setAuthEndpoint(config.getSynapseAuthEndpoint());
            client.setFileEndpoint(config.getSynapseFileEndpoint());
            return client;
        }
    }
    
}
