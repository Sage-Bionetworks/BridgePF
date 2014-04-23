angular.module('bridge').controller('ApplicationController', 
['$scope', '$http', '$location', '$modal', '$humane', '$window', 'authService', 'requestResetPasswordService', 
function($scope, $http, $location, $modal, $humane, $window, authService, requestResetPasswordService) {

	var DEFAULT_PATHS = ["","/","index.html","/index.html"];
	
	$scope.credentials = {};
	$scope.session = authService;
	
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
	    authService.signIn($scope.credentials);
		$scope.credentials.password = '';
	};
	$scope.signOut = function() {
	    authService.signOut();
	};
    $scope.resetPassword = function() {
        requestResetPasswordService.open();
    };

}]);
