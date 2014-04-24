describe("ApplicationController", function() {
	
	var ApplicationController, $rootScope;

	beforeEach(function() {
		module('bridge');
		
		$location = { path: jasmine.createSpy() };
		module(function($provide) {
			$provide.value('$location', $location);
		});
	});
	
	beforeEach(inject(function($injector) {
        $rootScope = $injector.get('$rootScope');
        var $controller = $injector.get('$controller');
        createController = function() {
        	return $controller('ApplicationController', {'$scope' : $rootScope });
        };
	}));	

	it('A variety of URL defaults are correctly marked as the default page', function() {
		ApplicationController = createController();

		var tab = $rootScope.tabs[3]; // the last, default tab
		
		$location.path = function() { return "/"; }
		expect($rootScope.tabClass(tab)).toEqual(true);
		
		$location.path = function() { return "/index.html"; }
		expect($rootScope.tabClass(tab)).toEqual(true);
		
		$location.path = function() { return ""; }
		expect($rootScope.tabClass(tab)).toEqual(true);

		$location.path = function() { return "/#/journal"; }
		expect($rootScope.tabClass(tab)).toEqual(false);
	});
	it("will not allow anonymous to access an authenticated page", function() {
		ApplicationController = createController();
		
		var event = {preventDefault: angular.identity};
		var next = current = {access: {allowAnonymous: false}};
		
		$rootScope.$broadcast("$routeChangeStart", next, {});
		
		expect($location.path).toHaveBeenCalledWith("/");
	});
	it("will allow access to authenticated page when logged in", function() {
		ApplicationController = createController();
		$rootScope.session.authenticated = true;
		
		var event = {preventDefault: angular.identity};
		var next = current = {access: {allowAnonymous: false}};
		$rootScope.$broadcast("$routeChangeStart", next, {});

		expect($location.path).not.toHaveBeenCalledWith("/");
	});
});