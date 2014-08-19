describe("ResetPasswordController", function() {
   
    var ResetPasswordController, $rootScope, $httpBackend, $window, $humane;

    beforeEach(function() {
        module('bridge');

        $humane = {confirm: jasmine.createSpy(), error: jasmine.createSpy(), status: jasmine.createSpy()};
        $window = {_session: {sessionToken: 'foo'}, location: {replace: jasmine.createSpy()}};
        module(function($provide) {
            $provide.value('$route', {current:{params:{sptoken: "abc"}}});
            $provide.value('$humane', $humane);
            $provide.value('$window', $window);
        });
    });
    
    beforeEach(inject(function($injector) {
        $rootScope = $injector.get('$rootScope');
        $httpBackend = $injector.get('$httpBackend');
        
        var $controller = $injector.get('$controller');
        createController = function() {
            return $controller('ResetPasswordController', {'$scope' : $rootScope });
        };
    }));
    afterEach(function() {
        $httpBackend.verifyNoOutstandingExpectation();
        $httpBackend.verifyNoOutstandingRequest();
    });

    function setupPOST() {
        // verify that the password is posted along with the session token in the header, 
        // as was extracted from the routing service.
        return $httpBackend.expectPOST('/api/auth/resetPassword', {password: "P4ssword", sptoken: "abc"});
    }

    it("submitting wrong session token shows an error", function() {
        ResetPasswordController = createController();
        $rootScope.resetPasswordForm = {$valid: true};
        $rootScope.sptoken = "abc";
        $rootScope.password = "P4ssword";

        setupPOST().respond(500, {"message":"Invalid session token (abc)"});
        $rootScope.submit();
        $httpBackend.flush();
        expect($humane.status.calls.argsFor(0)[0].data.message).toEqual("Invalid session token (abc)");
    });
    it("submitting correctly shows a confirmation message", function() {
        ResetPasswordController = createController();
        $rootScope.resetPasswordForm = {$valid: true};
        $rootScope.password = "P4ssword";

        setupPOST().respond(200, {});
        $rootScope.submit();
        $httpBackend.flush();
        
        expect($window.location.replace).toHaveBeenCalledWith("/#/?msg=passwordChanged");
    });

});