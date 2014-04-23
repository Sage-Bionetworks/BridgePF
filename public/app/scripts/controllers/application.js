angular.module('bridge').controller('ApplicationController', 
['$scope', '$http', '$location', '$modal', '$humane', '$window', 'SessionService', 'RequestResetPasswordService', 
function($scope, $http, $location, $modal, $humane, $window, SessionService, RequestResetPasswordService) {

	var DEFAULT_PATHS = ["","/","index.html","/index.html"];
	
	$scope.credentials = {};
	$scope.session = SessionService;
	
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
	    if (!$scope.credentials.username && !$scope.credentials.password) {
	        return;
	    }
		$http.post('/api/auth/signIn', angular.extend({}, $scope.credentials))
			.success(function(data, status) {
				SessionService.init(data.payload);
			}).error(function(data, status) {
				if (status === 412) {
				    $location.path("/consent/" + data.sessionToken);
				} else if (status === 404 || status === 401) {
					$humane.error("Wrong user name or password.");
				} else {
				    $humane.error("There has been an error.");
				}
			});
		$scope.credentials.password = '';
	};
	$scope.signOut = function() {
		$http.get('/api/auth/signOut')
			.success(function(data, status) {
				// Because of all the user data that could be in the browser, just refresh.
				$window.location.replace("/");
			}).error(function(data) {
			    $humane.error(data.payload);
			});
	};
    $scope.resetPassword = function() {
        RequestResetPasswordService.open();
    };

	// Anonymous users can't access user routes.
    // TODO: Should this just go in a config instead?
	$scope.$root.$on('$routeChangeStart', function(e, next, current) {
		if (!next.access.allowAnonymous && !$scope.session.authenticated) {
			console.warn("Page requires authentication, redirecting");
			e.preventDefault();
			$location.path("/");
		}
	});
	
}]);
