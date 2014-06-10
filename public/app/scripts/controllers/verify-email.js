bridge.controller('VerifyEmailController', ['$scope', '$route', 'authService', 'formService',  
function($scope, $route, authService, formService) {
    
    formService.initScope($scope, 'noForm');
    $scope.setMessage("Verifying...");

    $scope.sptoken = formService.retrieveSpToken($route);
    
    authService.verifyEmail({sptoken: $scope.sptoken}).then(function() {
        $scope.setMessage("Your email address has been verified. You can now sign in to Bridge and begin using the application.");
    }, function(response) {
        $scope.setMessage(response.data.payload, "danger");
    });
    
}]);