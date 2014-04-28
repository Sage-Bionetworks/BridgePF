/**
 * This functionality is fairly complex; pulled out and tested separately.
 */
describe("ApplicationController authentication support", function() {
    
    var controller, authService, $rootScope, $httpBackend;

    beforeEach(function() {
        module('bridge');
        
        $window = {
            location: { replace: jasmine.createSpy()} 
        };
        $humane = {
            confirm: jasmine.createSpy(),
            error: jasmine.createSpy()
        }
        $location = {
            path: jasmine.createSpy()
        }
        module(function($provide) {
            $provide.value('$window', $window);
            $provide.value('$humane', $humane);
            $provide.value('$location', $location);
        });
    });
    
    beforeEach(inject(function($injector) {
        $httpBackend = $injector.get('$httpBackend');
        $rootScope = $injector.get('$rootScope');
        authService = $injector.get("authService");
        var $controller = $injector.get('$controller');
        controller = $controller('ApplicationController', {'$scope' : $rootScope });
    }));    
    afterEach(function() {
        $httpBackend.verifyNoOutstandingExpectation();
        $httpBackend.verifyNoOutstandingRequest();
    }); 
    function expectNotLoggedIn() {
        expect(authService.authenticated).toEqual(false);
        expect(authService.username).toEqual("");
        expect(authService.sessionToken).toEqual("");
    }
    
    it('calls the sign in service on a sign in', function() {
        $httpBackend.expectPOST('/api/auth/signIn').respond({
            "type":"org.sagebionetworks.repo.model.auth.Session",
            "payload":{sessionToken: "someToken", username: "test2", authenticated: true}
        });
        $rootScope.credentials = { "username": "test2", "password": "password" };
        $rootScope.signIn();
        $httpBackend.flush();
        
        expect(authService.authenticated).toEqual(true);
        expect(authService.username).toEqual("test2");
        expect(authService.sessionToken).toEqual("someToken");
    });
    it("does not authenticate user with bad credentials", function() {
        $httpBackend.expectPOST('/api/auth/signIn').respond(404, {payload: "Wrong user name or password."});
        $rootScope.credentials = { "username": "asdf", "password": "asdf" };
        $rootScope.signIn();
        $httpBackend.flush();
        expect($humane.error).toHaveBeenCalledWith("Wrong user name or password.");
        expectNotLoggedIn();
    });
    it("redirects to the consent page when TOS hasn't been signed", function() {
        $httpBackend.expectPOST('/api/auth/signIn').respond(412, {sessionToken: "abc"});
        $rootScope.credentials = { "username": "asdf", "password": "asdf" };
        $rootScope.signIn();
        $httpBackend.flush();
        expect($location.path).toHaveBeenCalledWith("/consent/abc");
        expectNotLoggedIn();
    });
    it('calls the sign out service on a sign out', function() {
        $httpBackend.expectGET('/api/auth/signOut').respond({type: "StatusMessage", message: "Signed Out"});
        $rootScope.signOut();
        $httpBackend.flush();
        expect($window.location.replace).toHaveBeenCalledWith('/');
    });
    it("clears the password in credentials after sign-in", function() {
        $httpBackend.expectPOST('/api/auth/signIn').respond(404, {});
        $rootScope.credentials = { "username": "asdf", "password": "asdf" };
        $rootScope.signIn();
        $httpBackend.flush();
        expect($rootScope.credentials.password).toEqual("");
        expectNotLoggedIn();
    });
});