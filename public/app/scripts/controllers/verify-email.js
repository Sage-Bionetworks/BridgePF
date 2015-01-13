bridge.controller('VerifyEmailController', ['$scope', '$route', 'authService', 'formService',  
function($scope, $route, authService, formService) {
    
    formService.initScope($scope, 'noForm');
    $scope.setMessage("Verifying...");
    $scope.succeeded = false;
    $scope.sptoken = formService.retrieveSpToken($route);

    authService.verifyEmail({sptoken: $scope.sptoken}).then(function(response) {
        $scope.setMessage("");
        $scope.succeeded = 'loaded';
    }, function(response) {
        if (response.status === 412) {
            // authService.initSession(response.data);
            // Just exactly as if nothing had happened.
            $scope.setMessage("Your email address has been verified.");
        } else {
            if (response.data.message === "The requested resource does not exist.") {
                response.data.message = "Your email address has already been verified.";
            }
            $scope.setMessage(response.data.message, "danger");
        }
    });
    
}]);