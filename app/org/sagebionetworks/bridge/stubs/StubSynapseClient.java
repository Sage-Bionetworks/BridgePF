package org.sagebionetworks.bridge.stubs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import net.sf.cglib.transform.impl.UndeclaredThrowableStrategy;

import org.apache.commons.io.FileUtils;
import org.apache.http.client.ClientProtocolException;
import org.sagebionetworks.client.SynapseAdminClient;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import org.sagebionetworks.client.exceptions.SynapseClientException;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;
import org.sagebionetworks.repo.model.ACCESS_TYPE;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.DomainType;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.PaginatedResults;
import org.sagebionetworks.repo.model.PaginatedResultsUtil;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.TeamMembershipStatus;
import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.model.UserSessionData;
import org.sagebionetworks.repo.model.auth.NewIntegrationTestUser;
import org.sagebionetworks.repo.model.auth.NewUser;
import org.sagebionetworks.repo.model.auth.Session;
import org.sagebionetworks.repo.model.auth.UserEntityPermissions;
import org.sagebionetworks.repo.model.dao.WikiPageKey;
import org.sagebionetworks.repo.model.file.FileHandle;
import org.sagebionetworks.repo.model.file.FileHandleResults;
import org.sagebionetworks.repo.model.file.S3FileHandle;
import org.sagebionetworks.repo.model.principal.AliasCheckRequest;
import org.sagebionetworks.repo.model.principal.AliasCheckResponse;
import org.sagebionetworks.repo.model.principal.AliasType;
import org.sagebionetworks.repo.model.v2.wiki.V2WikiHeader;
import org.sagebionetworks.repo.model.v2.wiki.V2WikiPage;
import org.sagebionetworks.repo.model.versionInfo.SynapseVersionInfo;
import org.sagebionetworks.repo.web.NotFoundException;
import org.sagebionetworks.schema.adapter.JSONEntity;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

public abstract class StubSynapseClient implements SynapseClient, SynapseAdminClient {
	
	UserSessionData currentUserData;
	String sessionToken; // when this isn't separate from UserSessionData.getProfile().getSession() there are errors.
	Set<String> agreedTOUs = Sets.newHashSet();
	Map<String,UserSessionData> usersById = Maps.newHashMap();
	Map<String,AccessControlList> aclsByEntityId = Maps.newHashMap(); // treating community as an entity
	Map<String,V2WikiPage> wikiPagesById = Maps.newHashMap();
	Map<String,Team> teamsById = Maps.newHashMap();
	Multimap<Team,String> teamMemberships = LinkedListMultimap.create();
	
	Map<String,String> markdownsByFileHandleId = Maps.newHashMap();
	Map<String,String> emailByUserId = Maps.newHashMap();

	int idCount = 2;

	public StubSynapseClient() {
		/* Are we going to use this now? You have to reach into Synapse to do this.
		Team team = new Team();
		team.setId(TeamConstants.BRIDGE_ADMINISTRATORS.toString());
		teamsById.put(TeamConstants.BRIDGE_ADMINISTRATORS.toString(), team);
		*/
	}

	private static StubSynapseClient singleStub = null;

	public static StubSynapseClient createInstance() {
		if (singleStub == null) {
			// Configure CGLIB Enhancer...
			Enhancer enhancer = new Enhancer();
			enhancer.setSuperclass(StubSynapseClient.class);
			enhancer.setStrategy(new UndeclaredThrowableStrategy(UndeclaredThrowableException.class));
			enhancer.setInterfaces(new Class[] { SynapseClient.class, SynapseAdminClient.class });
			enhancer.setInterceptDuringConstruction(false);
			enhancer.setCallback(new MethodInterceptor() {
				@Override
				public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
					return proxy.invokeSuper(obj, args);
				}
			});

			// Generate the proxy class and create a proxy instance.
			Object proxy = enhancer.create();
			singleStub = (StubSynapseClient)proxy;
		}
		return singleStub;
	}

	@Override
	public SynapseVersionInfo getVersionInfo() {
		SynapseVersionInfo info = new SynapseVersionInfo();
		info.setVersion("UI stub");
		return info;
	}
	
	@Override
	public long createUser(NewIntegrationTestUser user) throws SynapseException, JSONObjectAdapterException {
		String id = newId();
		UserProfile profile = new UserProfile();
		profile.setUserName(user.getUsername());
		profile.setOwnerId(id);
		Session session = new Session();
		session.setSessionToken("session"+id);
		UserSessionData data = new UserSessionData();
		data.setIsSSO(false);
		data.setProfile(profile);
		data.setSession(session);
		usersById.put(user.getUsername(), data);
		usersById.put(id, data);
		emailByUserId.put(id, user.getEmail());
		return Long.parseLong(id);
	}

	/*
	private V2WikiPage createWikiPage(UserSessionData user, String title, String parentId, String markdown) {
		String email = emailByUserId.get(user.getProfile().getOwnerId());
		
		V2WikiPage page = new V2WikiPage();
		page.setTitle(title);
		page.setCreatedBy(email);
		page.setCreatedOn(new Date());
		page.setId(newId());
		if (parentId != null) {
			page.setParentWikiId(parentId);
		}
		page.setMarkdownFileHandleId(newId());
		markdownsByFileHandleId.put(page.getMarkdownFileHandleId(), markdown);
		wikiPagesById.put(page.getId(), page);
		return page;
	}
	
	private void addToAccessControlList(String entityId, String userOwnerId, ACCESS_TYPE... types) {
		AccessControlList acl = aclsByEntityId.get(entityId);
		if (acl == null) {
			acl = new AccessControlList();
			acl.setId(entityId);
			aclsByEntityId.put(entityId, acl);
		}
		ResourceAccess selected = null;
		if (acl.getResourceAccess() == null) {
			acl.setResourceAccess(new HashSet<ResourceAccess>());
		}
		for (ResourceAccess ra : acl.getResourceAccess()) {
			if (ra.getPrincipalId() != null && userOwnerId.equals(ra.getPrincipalId().toString())) {
				selected = ra;
				break;
			}
		}
		if (selected == null) {
			selected = new ResourceAccess();
			selected.setAccessType(new HashSet<ACCESS_TYPE>());
			selected.setPrincipalId(Long.parseLong(userOwnerId));
			acl.getResourceAccess().add(selected);
		}
		selected.getAccessType().addAll(Sets.newHashSet(types));
	}
	*/
	
	private String newId() {
		return Integer.toString(++idCount);
	}
	
	private <T extends JSONEntity> PaginatedResults<T> toResults(List<T> list) {
		PaginatedResults<T> results = new PaginatedResults<T>();
		results.setResults(list);
		results.setTotalNumberOfResults(list.size());
		return results;
	}

	@Override
	public void appendUserAgent(String toAppend) {
	}

	@Override
	public void setSessionToken(String sessionToken) {
		this.sessionToken = sessionToken;
	}

	@Override
	public String getCurrentSessionToken() {
		return this.sessionToken;
	}
	
	@Override
	public Session login(String userName, String password) throws SynapseException {
		UserSessionData data = usersById.get(userName);
		if (data == null) {
			throw new SynapseClientException();
		}
		currentUserData = data;
		Session session = data.getSession();
		session.setAcceptsTermsOfUse(true);
		if (!agreedTOUs.contains(data.getProfile().getOwnerId())) {
			session.setAcceptsTermsOfUse(false);
		}
		data.setSession(session);
		return session;		
	}

	@Override
	public void logout() throws SynapseException {
		currentUserData = null;
	}

	@Override
	public UserSessionData getUserSessionData() throws SynapseException {
		if (currentUserData == null) {
			throw new SynapseClientException();
		}
		return currentUserData;
	}

	@Override
	public S3FileHandle createFileHandle(File temp, String contentType) throws SynapseException, IOException {
		// If this was compressed, uncompress it before storing it. The actual implementation stores compressed
		// and only uncompressed when you call downloadV2WikiMarkdown(). 
		String markdown = null;
		try {
			markdown = org.sagebionetworks.downloadtools.FileUtils.readCompressedFileAsString(temp);	
		} catch(Exception e) {
			// assume it wasn't compressed.
			markdown = FileUtils.readFileToString(temp);
		}
		if (markdown == null) {
			throw new SynapseClientException(new NotFoundException());
		}
		S3FileHandle handle = new S3FileHandle();
		String id = newId();
		handle.setId(id);
		handle.setFileName(temp.getName());
		
		markdownsByFileHandleId.put(id, markdown);

		return handle;
	}
	

	@Override
	public FileHandleResults createFileHandles(List<File> files) throws SynapseException {
		List<FileHandle> handles = Lists.newArrayListWithCapacity(files.size());
		for (File file : files) {
			try {
				String mimeType = SynapseClientImpl.guessContentTypeFromStream(file);
				FileHandle handle = createFileHandle(file, mimeType);
				handles.add(handle);
			} catch(IOException ioe) {
				throw new SynapseClientException(ioe);
			}
		}
		FileHandleResults results = new FileHandleResults();
		results.setList(handles);
		return results;
	}

	@Override
	public String getUserName() {
		if (currentUserData != null && currentUserData.getProfile() != null) {
			return currentUserData.getProfile().getUserName();
		}
		return null;
	}

	@Override
	public AccessControlList getACL(String entityId) throws SynapseException {
		return aclsByEntityId.get(entityId);		
	}

	@Override
	public void updateMyProfile(UserProfile userProfile) throws SynapseException {
		UserSessionData data = usersById.get(userProfile.getOwnerId());
		data.setProfile(userProfile);		
	}

	@Override
	public UserProfile getUserProfile(String ownerId) throws SynapseException {
		return usersById.get(ownerId).getProfile();
	}

	@Override
	public AccessControlList createACL(AccessControlList acl) throws SynapseException {
		aclsByEntityId.put(acl.getId(), acl);
		return acl;
	}

	@Override
	public UserEntityPermissions getUsersEntityPermissions(String entityId) throws SynapseException {
		UserEntityPermissions permits = new UserEntityPermissions();
		permits.setCanAddChild(false);
		permits.setCanChangePermissions(false);
		permits.setCanDelete(false);
		permits.setCanDownload(false);
		permits.setCanEdit(false);
		permits.setCanEnableInheritance(false);
		permits.setCanPublicRead(false);
		permits.setCanView(false);
		AccessControlList acl = aclsByEntityId.get(entityId);
		if (acl != null) {
			for (ResourceAccess ra : acl.getResourceAccess()) {
				if (ra.getPrincipalId().toString().equals(currentUserData.getProfile().getOwnerId())) {
					for (ACCESS_TYPE type : ra.getAccessType()) {
						if (type == ACCESS_TYPE.UPDATE) {
							permits.setCanEdit(true);			
						} else if (type == ACCESS_TYPE.CHANGE_PERMISSIONS) {
							permits.setCanChangePermissions(true);			
						}
					}
				}
			}
		}
		return permits;
	}

	@Override
	public V2WikiPage createV2WikiPage(String ownerId, ObjectType ownerType, V2WikiPage page)
			throws JSONObjectAdapterException, SynapseException {
		String id = newId();
		page.setId(id);
		wikiPagesById.put(page.getId(), page);
		return page;
		
	}

	@Override
	public V2WikiPage getV2WikiPage(WikiPageKey key) throws JSONObjectAdapterException, SynapseException {
		String realId = key.getWikiPageId();
		if (!wikiPagesById.containsKey(realId)) {
			throw new SynapseClientException("Wiki page not found");
		}
		return wikiPagesById.get(realId);
	}

	@Override
	public V2WikiPage updateV2WikiPage(String ownerId, ObjectType ownerType, V2WikiPage page)
			throws JSONObjectAdapterException, SynapseException {
		if (!wikiPagesById.containsKey(page.getId())) {
			throw new SynapseClientException("Wiki page does not yet exist");
		}
		wikiPagesById.put(page.getId(), page);
		return page;
	}

	/*
	@Override
	public V2WikiPage getV2RootWikiPage(String ownerId, ObjectType ownerType) throws JSONObjectAdapterException, SynapseException {
		// The invisible root wiki page is stored under the community key.
		Community community = communitiesById.get(ownerId);
		if (community == null) {
			throw new SynapseClientException("Wiki page's community not found");
		}
		String communityId = community.getId();
		if (!wikiPagesById.containsKey(communityId)) {
			throw new SynapseClientException("Wiki page not found");
		}
		return wikiPagesById.get(communityId);
	}
	*/

	@Override
	public FileHandleResults getV2WikiAttachmentHandles(WikiPageKey key) throws JSONObjectAdapterException,
			SynapseException {
		if (!wikiPagesById.containsKey(key.getWikiPageId())) {
			throw new SynapseClientException("Wiki not found");
		}
		FileHandleResults results = new FileHandleResults();
		V2WikiPage page = wikiPagesById.get(key.getWikiPageId());
		
		// I think we need to save and restore these...
		List<FileHandle> handles = Lists.newArrayListWithCapacity(page.getAttachmentFileHandleIds().size());
		if (page.getAttachmentFileHandleIds() != null) {
			for (String id : page.getAttachmentFileHandleIds()) {
				S3FileHandle handle = new S3FileHandle();
				handle.setId(id);
				handles.add(handle);
			}
		}
		results.setList(handles);
		return results;
	}

	@Override
	public void deleteV2WikiPage(WikiPageKey key) throws SynapseException {
		wikiPagesById.remove(key.getWikiPageId());
	}

	@Override
	public PaginatedResults<V2WikiHeader> getV2WikiHeaderTree(String ownerId, ObjectType ownerType)
			throws SynapseException, JSONObjectAdapterException {
		List<V2WikiHeader> list = Lists.newArrayList();

		// get the root, add it
		V2WikiPage root = wikiPagesById.get(ownerId);
		addToResults(list, root);
		
		// get every page under the root (we only have a shallow list, not a tree), add it
		for (V2WikiPage page : wikiPagesById.values()) {
			if (page.getParentWikiId() != null && page.getParentWikiId().equals(root.getId())) {
				addToResults(list, page);
			}
		}
		return toResults(list);
	}
	
	private void addToResults(List<V2WikiHeader> list, V2WikiPage page) {
		V2WikiHeader header = new V2WikiHeader();
		header.setId(page.getId());
		header.setParentId(page.getParentWikiId());
		header.setTitle(page.getTitle());
		list.add(header);
	}
	
	@Override
	public String getTermsOfUse(DomainType domain) throws SynapseException {
		if (domain != DomainType.BRIDGE) {
			throw new IllegalArgumentException("Should not call getTermsOfUse() with the Synapse domain");
		}
		return "<p>These are the Stub terms of use.</p>";
	}

	@Override
	public Team getTeam(String id) throws SynapseException {
		if (!teamsById.keySet().contains(id)) {
			throw new SynapseClientException("Team not found");
		}
		return teamsById.get(id);
	}

	@Override
	public TeamMembershipStatus getTeamMembershipStatus(String teamId, String userId) throws SynapseException {
		TeamMembershipStatus status = new TeamMembershipStatus();
		status.setTeamId(teamId);
		status.setUserId(userId);
		Team team = teamsById.get(teamId);
		if (team != null) {
			Collection<String> members = teamMemberships.get(team);
			status.setIsMember( (members != null && members.contains(userId)) );
		} else {
			status.setIsMember(false);
		}
		return status;
	}

	/**
	 * This returns false if either the userName or the email are a duplicate.
	 */
	@Override
	public AliasCheckResponse checkAliasAvailable(AliasCheckRequest request) throws SynapseException {
		String value = request.getAlias();
		AliasCheckResponse response = new AliasCheckResponse();
		response.setAvailable(true);
		for (UserSessionData data : usersById.values()) {
			String email = emailByUserId.get(data.getProfile().getOwnerId());
			if (email.equals(value) && request.getType() == AliasType.USER_EMAIL) {
				response.setAvailable(false);
			}
			String userName = data.getProfile().getUserName();
			if (userName.equals(value) && request.getType() == AliasType.USER_NAME) {
				response.setAvailable(false);
			}
		}
		return response;
	}
	
	@Override
	public void createUser(NewUser user) throws SynapseException {
		if (usersById.get(user.getUserName()) != null) {
			throw new SynapseClientException("Service Error(409): FAILURE: Got HTTP status 409 for  Response Content: {\"reason\":\"User '"+user.getUserName()+"' already exists\n\"}");
		}
		// Check email too
		for (UserSessionData data : usersById.values()) {
			String email = emailByUserId.get(data.getProfile().getOwnerId());
			if (user.getEmail().equals(email)) {
				throw new SynapseClientException("Service Error(409): FAILURE: Got HTTP status 409 for  Response Content: {\"reason\":\"User email '"+email+"' already exists\n\"}");
			}
		}

		String USER_ID = newId();
		emailByUserId.put(USER_ID, user.getEmail());
		
		UserProfile profile = new UserProfile();
		profile.setUserName(user.getUserName());
		profile.setFirstName(user.getFirstName());
		profile.setLastName(user.getLastName());
		profile.setOwnerId(USER_ID);
		Session session = new Session();
		session.setSessionToken(USER_ID);
		UserSessionData data = new UserSessionData();
		data.setSession(session);
		data.setProfile(profile);
		// ARGH!
		usersById.put(user.getUserName(), data);
		usersById.put(USER_ID, data);		
	}

	@Override
	public void changePassword(String sessionToken, String newPassword) throws SynapseException {
		// noop
	}

	@Override
	public void signTermsOfUse(String sessionToken, DomainType domain, boolean acceptTerms) throws SynapseException {
		if (domain != DomainType.BRIDGE) {
			throw new IllegalArgumentException("Don't call this method with any other domain than Bridge");
		}
		if (acceptTerms) {
			agreedTOUs.add(currentUserData.getProfile().getOwnerId());	
		}		
	}

	@Override
	public Session passThroughOpenIDParameters(String queryString, Boolean createUserIfNecessary)
			throws SynapseException {
		throw new IllegalArgumentException("Don't use this API method in Bridge");
	}

	@Override
	public Session passThroughOpenIDParameters(String queryString, Boolean createUserIfNecessary,
			DomainType domainType) throws SynapseException {
		// We'd like to test three scenarios here:
		// 1. Brand new user, needs to sign TOU
		// 2. Returning user who hasn't signed TOU?
		// 3. Returning user who should just be logged in to default start page
		currentUserData = usersById.values().iterator().next();
		Session session = new Session();
		session.setSessionToken(currentUserData.getProfile().getOwnerId());
		return session;		
	}
	
	@Override
	public String downloadV2WikiMarkdown(WikiPageKey key) throws ClientProtocolException, FileNotFoundException,
			IOException {
		V2WikiPage page = wikiPagesById.get(key.getWikiPageId());
		if (page == null) {
			throw new FileNotFoundException("Wiki page not found");
		}
		String markdown = markdownsByFileHandleId.get(page.getMarkdownFileHandleId());
		if (markdown == null) {
			throw new FileNotFoundException("Wiki page markdown not found");
		}
		return markdown;
	}
	
	@Override
	public void sendPasswordResetEmail(String email) throws SynapseException {
		// noop
	}

	@Override
	public void setAuthEndpoint(String authEndpoint) {
		// noop
	}
	
	@Override
	public PaginatedResults<Team> getTeams(String fragment, long limit, long offset) throws SynapseException {
		if (fragment == null) {
			throw new IllegalArgumentException("Must have a search fragment string");
		}
		List<Team> teams = Lists.newArrayList();
		for (Team team : teamsById.values()) {
			if (team.getName() != null) {
				if (team.getName().indexOf(fragment) > -1) {
					teams.add(team);
				}
			}
		}
		return PaginatedResultsUtil.createPaginatedResults(teams, limit, offset);
	}
	
	@Override
	public Team createTeam(Team team) throws SynapseException {
		if (team.getId() == null) {
			team.setId(newId());	
		}
		teamsById.put(team.getId(), team);
		return team;
	}
	
	@Override
	public void addTeamMember(String teamId, String memberId) throws SynapseException {
		Team team = teamsById.get(teamId);
		if (team == null) {
			throw new SynapseNotFoundException("Could not find team #"+teamId);
		}
		UserSessionData member = usersById.get(memberId);
		if (member == null) {
			throw new SynapseNotFoundException("Could not find member #"+memberId);
		}
		teamMemberships.put(team, memberId);
	}
}
