bridge.controller('ApplicationController', 
['$scope', '$rootScope', '$location', '$humane', '$window', 'authService', 'modalService', 'signInService',  
function($scope, $rootScope, $location, $humane, $window, authService, modalService, signInService) {
    
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
	    modalService.openModal('SignUpModalController', 'lg', 'views/dialogs/signUp.html');
	};
	$scope.signOut = function() {
	    authService.signOut().then(function() {
	        $window.location.replace("/");  
	    }, $humane.status);
	};
    $scope.resetPassword = function() {
        modalService.openModal('RequestResetPasswordModalController', 'sm', '/shared/views/requestResetPassword.html');
    };
}]);
