describe('Controller: MainCtrl', function() {
	
	var MainController, scope, $httpBackend;

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
	
	it('should call the test web service', function() {
		$httpBackend.expectGET('/api/test').respond({message: 'Hello kitty world'});
		MainController = createController();
		$httpBackend.flush();
		console.log(MainController);
	});
});
