bridge.controller('VerifyEmailController', ['$scope', '$route', '$cookies', 'authService', 'formService',  
function($scope, $route, $cookies, authService, formService) {
    
    formService.initScope($scope, 'noForm');
    $scope.setMessage("Verifying...");

    $scope.sptoken = formService.retrieveSpToken($route);
    
    authService.verifyEmail({sptoken: $scope.sptoken}).then(function(response) {
        // Normally verify email throws an exception that generates an HTTP error 
        // response, so this will probably not happen.
        $cookies['Bridge-Session'] = response.data.payload.sessionToken;
        $scope.setMessage("Your email address has been verified. Thank you!");
    }, function(response) {
        if (response.status === 412) {
            $cookies['Bridge-Session'] = response.data.payload.sessionToken;
            authService.handleConsent(response.data.payload);
        } else {
            $scope.setMessage(response.data.payload, "danger");    
        }
    });
    
}]);