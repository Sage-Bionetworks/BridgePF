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
    
    // This just tests the presence of a dialog, tests of the functionality go to the tests for 
    // the signInService which now includes a modal dialog.
    it('shows the sign in form', function() {
        $httpBackend.expectGET('views/dialogs/signIn.html').respond(200);
        $rootScope.signIn();
        $httpBackend.flush();
    });
    it('shows the reset password form', function() {
        $httpBackend.expectGET('views/dialogs/requestResetPassword.html').respond(500);
        $rootScope.resetPassword();
        $httpBackend.flush();
    });
    it('calls the sign out service on a sign out', function() {
        $httpBackend.expectGET('/api/auth/signOut').respond({type: "StatusMessage", message: "Signed Out"});
        $rootScope.signOut();
        $httpBackend.flush();
        expect($window.location.replace).toHaveBeenCalledWith('/');
    });
});