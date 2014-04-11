describe('Controller: MainCtrl', function() {
	
	var MainController, $rootScope, $httpBackend;

	beforeEach(module('bridge'));
	
	beforeEach(inject(function($injector) {
        $httpBackend = $injector.get('$httpBackend');
        $rootScope = $injector.get('$rootScope');
        var $controller = $injector.get('$controller');
        createController = function() {
        	return $controller('MainController', {'$scope' : $rootScope });
        };
	}));	

    afterEach(function() {
    	$httpBackend.verifyNoOutstandingExpectation();
    	$httpBackend.verifyNoOutstandingRequest();
    });	
	
	it('should call the signIn web service', function() {
		$httpBackend.expectPOST('/api/auth/signIn').respond({
			"type":"org.sagebionetworks.repo.model.auth.Session",
			"payload":{"sessionToken": "1ed844ba-953e-4260-82b2-89aa9d4855ff"}
		});
		MainController = createController();
		$rootScope.credentials = { "username": "test2", "password": "password" };
		$rootScope.signIn();
		$httpBackend.flush();
	});
});
