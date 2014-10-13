bridge.controller('ResetPasswordController', ['$scope', '$route', '$humane', '$window', 'authService', 'formService',  
function($scope, $route, $humane, $window, authService, formService) {
    
    authService.clear();
    
    $scope.sptoken = formService.retrieveSpToken($route);
    formService.initScope($scope, 'resetPasswordForm');
    
    $scope.submit = function() {
        authService.resetPassword($scope.password, $scope.sptoken).then(function() {
            console.log(window.location);
            console.log("/#/?msg=passwordChanged");
            // $window.location.replace("/#/?msg=passwordChanged");
        }, $humane.status);
    };
    
}]);