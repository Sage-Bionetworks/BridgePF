package org.sagebionetworks.bridge.stubs;

import org.sagebionetworks.client.SynapseAdminClient;
import org.sagebionetworks.client.SynapseClient;
import org.springframework.context.ApplicationContext;

public class StubOnStartupHandler {
	
	public class StubClientProvider implements SynapseBootstrap.ClientProvider {
		private StubSynapseClient stub;
		public StubClientProvider(StubSynapseClient stub) {
			this.stub = stub;
		}
		@Override public SynapseAdminClient getAdminClient() { return stub; }
		@Override public SynapseClient getSynapseClient() { return stub; }
	}
	
	public void stub(ApplicationContext applicationContext) throws Exception {
		Object object = applicationContext.getBean("synapseClient");
		if (object instanceof StubSynapseClient) {
			SynapseBootstrap bootstrap = new SynapseBootstrap(new StubClientProvider((StubSynapseClient) object));
			bootstrap.create();
		}
	}
	
}
