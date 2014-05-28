describe("SignInService", function() {
    
    /* Won't help to test
    var fakeModal = {
        result: {
            then: function(confirmCallback, cancelCallback) {
                this.confirmCallBack = confirmCallback;
                this.cancelCallback = cancelCallback;
            }
        },
        close: function( item ) {
            this.result.confirmCallBack( item );
        },
        dismiss: function( type ) {
            this.result.cancelCallback( type );
        }
    };
    */
    
    var signInService, $httpBackend;

    // I don't even know where to START mocking this out... this is all WAY too complicated.
    
    beforeEach(function() {
        module('bridge');
        module(function($provide) {
            $provide.value('$modal', fakeModal);
        });
/*
        var $route = {current:{params:{sessionToken: "abc"}}};
        
        $humane = {
            confirm: jasmine.createSpy(),
            error: jasmine.createSpy()
        };
        module(function($provide) {
            $provide.value('$route', $route);
            $provide.value('$humane', $humane);
        });*/
    });
    
    beforeEach(inject(function($injector) {
        signInService = $injector.get('signInService');
        $httpBackend = $injector.get('$httpBackend');
    }));
    afterEach(function() {
        $httpBackend.verifyNoOutstandingExpectation();
        $httpBackend.verifyNoOutstandingRequest();
    });
    
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
    it("clears the password in credentials after sign-in", function() {
        // Getting the dialog
        $httpBackend.expectGET('views/dialogs/signIn.html').respond(200, {});
        $httpBackend.expectPOST('/api/auth/signIn').respond(404, {});
        $rootScope.credentials = { "username": "asdf", "password": "asdf" };
        $rootScope.signIn();
        $httpBackend.flush();
        expect($rootScope.credentials.password).toEqual("");
        expectNotLoggedIn();
    });
    

});