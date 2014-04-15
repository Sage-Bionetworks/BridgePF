angular.module('bridge').controller('ApplicationController', ['$scope', '$http', '$location', '$window', 'SessionService', 
function($scope, $http, $location, $window, SessionService) {

	var DEFAULT_PATHS = ["","/","index.html","/index.html"];
	
	$scope.credentials = {};
	$scope.session = SessionService;
	
	function handleError(data, status) {
		if (status === 401) {
			$window.alert("You must sign in to continue.");
		} else {
			$window.alert(data.payload);
		}
	}
	
	$scope.tabs = [
        {link: '/#/health', label: "My Health"},
        {link: '/#/questions', label: "Questions"},
        {link: '/#/journal', label: "Journal"},
        {link: '/#/research', label: "Researcher's Journal"}
    ]; 
	$scope.tabClass = function(tab) {
		var path = $location.path();
		if (DEFAULT_PATHS.indexOf(path) > -1) {
			path = "/research";
		}
		return (tab.link.indexOf(path) > -1);
	};
	
	$scope.signIn = function() {
		$http.post('/api/auth/signIn', angular.extend({}, $scope.credentials))
			.success(function(data, status) {
				SessionService.init(data.payload);
			}).error(function(data, status) {
				if (status === 412) {
					$window.alert("You must first sign the terms of use.");
				} else {
					$window.alert("Wrong user name or password.");	
				}
			});
		$scope.credentials.password = '';
	};
	$scope.signOut = function() {
		$http.get('/api/auth/signOut')
			.success(function(data, status) {
				// Because of all the user data that could be in the browser, just refresh.
				$window.location.replace("/");
			}).error(handleError);
	};
	$scope.resetPassword = function() {
		$http.post('/api/auth/resetPassword', {'email': 'test2@sagebase.org'})
			.success(function() {
				$window.alert("Not yet implemented");
			}).error(handleError);
	};
	
	// Anonymous users can't access user routes.
	$scope.$root.$on('$routeChangeStart', function(e, next, current) {
		if (!next.access.allowAnonymous && !$scope.session.authenticated) {
			console.warn("Page requires authentication, redirecting");
			e.preventDefault();
			$location.path("/");
		}
	});
	
}]);
