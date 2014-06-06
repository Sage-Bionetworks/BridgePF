bridge.controller('VerifyEmailController', ['$scope', '$route', 'authService', 'formService',  
function($scope, $route, authService, formService) {
    
    formService.initScope($scope);
    $scope.setMessage("Verifying...");
    
    // route.params don't work here given the way stormpath structures the URL
    $scope.sptoken = $route.current.params.sptoken;
    if (!$scope.sptoken) {
        $scope.sptoken = (document.location.search+"").split("sptoken=")[1];    
    }
    
    authService.verifyEmail({sptoken: $scope.sptoken}).then(function() {
        $scope.setMessage("Your email address has been verified. You can now sign in to Bridge and begin using the application.");
    }, function(data) {
        $scope.setMessage(data.payload, "danger");
    });
    
}]);