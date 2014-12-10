bridge.controller('VerifyEmailController', ['$scope', '$route', 'authService', 'formService',  
function($scope, $route, authService, formService) {
    
    formService.initScope($scope, 'noForm');
    $scope.setMessage("Verifying...");

    $scope.sptoken = formService.retrieveSpToken($route);
    
    authService.verifyEmail({sptoken: $scope.sptoken}).then(function(response) {
        $scope.setMessage("Your email address has been verified.");
    }, function(response) {
        if (response.status === 412) {
            // authService.initSession(response.data);
            // Just exactly as if nothing had happened.
            $scope.setMessage("Your email address has been verified.");
        } else {
            $scope.setMessage(response.data.message, "danger");    
        }
    });
    
}]);