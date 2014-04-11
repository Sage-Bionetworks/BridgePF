package org.sagebionetworks.bridge.stubs;

import org.sagebionetworks.StackConfiguration;
import org.sagebionetworks.client.SynapseAdminClient;
import org.sagebionetworks.client.SynapseAdminClientImpl;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.repo.model.DomainType;
import org.sagebionetworks.repo.model.auth.NewIntegrationTestUser;
import org.sagebionetworks.repo.model.auth.Session;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;

public class SynapseBootstrap {

	public interface ClientProvider {
		public SynapseAdminClient getAdminClient();
		public SynapseClient getSynapseClient();
	}
	
	public static class IntegrationClientProvider implements ClientProvider {
		private SynapseClient synapse;
		private SynapseAdminClient admin;
		
		public IntegrationClientProvider() {
			this.synapse = new SynapseClientImpl(DomainType.BRIDGE);
			setEndpoints(this.synapse);
			this.admin = new SynapseAdminClientImpl();
			setEndpoints(this.admin);
			this.admin.setUserName(StackConfiguration.getMigrationAdminUsername());
			this.admin.setApiKey(StackConfiguration.getMigrationAdminAPIKey());
		}
		
		@Override public SynapseAdminClient getAdminClient() { return this.admin; }
		@Override public SynapseClient getSynapseClient() { return this.synapse; }
		
		private void setEndpoints(SynapseClient client) {
			client.setAuthEndpoint(StackConfiguration.getAuthenticationServicePrivateEndpoint());
			client.setRepositoryEndpoint(StackConfiguration.getRepositoryServiceEndpoint());
			client.setFileEndpoint(StackConfiguration.getFileServiceEndpoint());
		}		
	}
	
	public static void main(String[] args) throws Exception {
		SynapseBootstrap bootstrapper = new SynapseBootstrap(new SynapseBootstrap.IntegrationClientProvider());
		bootstrapper.create();
	}
	
	private ClientProvider provider;
	
	public SynapseBootstrap(ClientProvider provider) {
		this.provider = provider;
	}
	
	public void create() throws Exception {
		createUser(false, provider.getAdminClient(), provider.getSynapseClient(), "test1", "test1@sagebase.org", false);
		createUser(true, provider.getAdminClient(), provider.getSynapseClient(), "test2", "test2@sagebase.org", true);
		createUser(true, provider.getAdminClient(), provider.getSynapseClient(), "test3", "test3@sagebase.org", true);
		createUser(false, provider.getAdminClient(), provider.getSynapseClient(), "test4", "test4@sagebase.org", true);
	}

	private void createUser(boolean isAdmin, SynapseAdminClient admin, SynapseClient synapse, String userName, String email, boolean acceptsTermsOfUse)
			throws SynapseException, JSONObjectAdapterException {
		try {
			NewIntegrationTestUser newUser = new NewIntegrationTestUser();
			newUser.setUsername(userName);
			newUser.setEmail(email);
			newUser.setPassword("password");
			try {
				admin.createUser(newUser);
			} catch (SynapseException e) {
				if (!e.getMessage().contains("already exists")) {
					throw e;
				}
			}
			
			Session session = synapse.login(userName, "password");
			if (acceptsTermsOfUse) {
				synapse.signTermsOfUse(session.getSessionToken(), DomainType.BRIDGE, true);
			}
			/* if (isAdmin) {
				UserSessionData data = synapse.getUserSessionData();
				admin.addTeamMember(TeamConstants.BRIDGE_ADMINISTRATORS.toString(), data.getProfile().getOwnerId());
			}*/
		} catch(Exception e) {
			e.printStackTrace();
			System.out.println("User '" + userName + "' already exists");
		}
	}

}
