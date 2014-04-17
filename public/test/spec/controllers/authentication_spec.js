/**
 * This functionality is fairly complex; pulled out and tested separately.
 */
describe("ApplicationController authentication support", function() {
	
	var ApplicationController, SessionService, $rootScope, $httpBackend;

	beforeEach(function() {
		module('bridge');
		
		$window = {
			alert: jasmine.createSpy(),
			location: { replace: jasmine.createSpy()} 
		};
		module(function($provide) {
			$provide.value('$window', $window);
		});
	});
	
	beforeEach(inject(function($injector) {
        $httpBackend = $injector.get('$httpBackend');
        $rootScope = $injector.get('$rootScope');
        SessionService = $injector.get("SessionService");
        var $controller = $injector.get('$controller');
        createController = function() {
        	return $controller('ApplicationController', {'$scope' : $rootScope });
        };
	}));	
    afterEach(function() {
    	$httpBackend.verifyNoOutstandingExpectation();
    	$httpBackend.verifyNoOutstandingRequest();
    });	
	function expectNotLoggedIn() {
		expect(SessionService.authenticated).toEqual(false);
		expect(SessionService.username).toEqual("");
		expect(SessionService.sessionToken).toEqual("");
	}
	
	it('calls the sign in service on a sign in', function() {
		$httpBackend.expectPOST('/api/auth/signIn').respond({
			"type":"org.sagebionetworks.repo.model.auth.Session",
			"payload":{sessionToken: "someToken", username: "test2", authenticated: true}
		});
		ApplicationController = createController();
		$rootScope.credentials = { "username": "test2", "password": "password" };
		$rootScope.signIn();
		$httpBackend.flush();
		
		expect(SessionService.authenticated).toEqual(true);
		expect(SessionService.username).toEqual("test2");
		expect(SessionService.sessionToken).toEqual("someToken");
	});
	it("does not authenticate user with bad credentials", function() {
		$httpBackend.expectPOST('/api/auth/signIn').respond(404, {payload: "Did not find a user with alias: asdf"});
		ApplicationController = createController();
		$rootScope.credentials = { "username": "asdf", "password": "asdf" };
		$rootScope.signIn();
		$httpBackend.flush();
		expect($window.alert).toHaveBeenCalledWith("Did not find a user with alias: asdf");
		expectNotLoggedIn();
	});
	it("does not authenticate user and alerts when TOS have not been signed", function() {
		$httpBackend.expectPOST('/api/auth/signIn').respond(412, {});
		ApplicationController = createController();
		$rootScope.credentials = { "username": "asdf", "password": "asdf" };
		$rootScope.signIn();
		$httpBackend.flush();
		expect($window.alert).toHaveBeenCalledWith("You must first sign the terms of use.");
		expectNotLoggedIn();
	});
	it('calls the sign out service on a sign out', function() {
		$httpBackend.expectGET('/api/auth/signOut').respond({type: "StatusMessage", message: "Signed Out"});
		ApplicationController = createController();
		$rootScope.signOut();
		$httpBackend.flush();
		expect($window.location.replace).toHaveBeenCalledWith('/');
	});
	it("clears the password in credentials after sign-in", function() {
		$httpBackend.expectPOST('/api/auth/signIn').respond(404, {});
		ApplicationController = createController();
		$rootScope.credentials = { "username": "asdf", "password": "asdf" };
		$rootScope.signIn();
		$httpBackend.flush();
		expect($rootScope.credentials.password).toEqual("");
		expectNotLoggedIn();
	});
});