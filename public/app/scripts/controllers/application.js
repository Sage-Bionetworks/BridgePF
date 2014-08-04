bridge.controller('ApplicationController', 
['$scope', '$rootScope', '$location', '$humane', '$window', 'authService', 'requestResetPasswordService', 'signInService', 'signUpService',  
function($scope, $rootScope, $location, $humane, $window, authService, requestResetPasswordService, signInService, signUpService) {
    
    $rootScope.loading = 0;
    $rootScope.$on("loadStart", function() {
        $rootScope.loading++;
    });
    $rootScope.$on("loadEnd", function() {
        $rootScope.loading--;
    });

	var DEFAULT_PATHS = ["","/","index.html","/index.html"];

    $scope.session = authService;

	$scope.tabs = [
        {link: '#/health', label: "My Health"},
        {link: '#/questions', label: "Questions"},
        {link: '#/journal', label: "Journal"},
        {link: '#/research', label: "Researcher's Journal"}
    ]; 
	$scope.tabClass = function(tab) {
		var path = $location.path();
		if (path.indexOf("/tracker/") > -1) {
		    path = "/health";
		}
		if (DEFAULT_PATHS.indexOf(path) > -1) {
			path = "/research";
		}
		return (tab.link.indexOf(path) > -1);
	};

	$scope.signIn = function() {
	    signInService.open();
	};
	$scope.signUp = function() {
	    signUpService.open();
	};
	$scope.signOut = function() {
	    authService.signOut().then(function() {
	        $window.location.replace("/");  
	    }, function(response) {
	        $humane.error(response.data.payload); 
	    });
	};
    $scope.resetPassword = function() {
        requestResetPasswordService.open();
    };
}]);
