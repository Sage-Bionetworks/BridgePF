bridge.controller('VerifyEmailController', ['$scope', '$route', 'authService', 'formService',  
function($scope, $route, authService, formService) {
    
    formService.initScope($scope, 'noForm');
    $scope.setMessage("Verifying...");

    $scope.sptoken = formService.retrieveSpToken($route);
    
    authService.verifyEmail({sptoken: $scope.sptoken}).then(function(response) {
        $scope.setMessage("Your email address has been verified. Thank you!");
    }, function(response) {
        if (response.status === 412) {
            authService.initSession(response.data.payload);
        } else {
            $scope.setMessage(response.data.payload, "danger");    
        }
    });
    
}]);