bridge.controller('VerifyEmailController', ['$scope', '$route', 'authService', function($scope, $route, authService) {
    
    $scope.message = "Verifying...";
    $scope.messageType = "info";
    
    // route.params don't work here given the way stormpath structures the URL
    $scope.sptoken = $route.current.params.sptoken;
    if (!$scope.sptoken) {
        $scope.sptoken = (document.location.search+"").split("sptoken=")[1];    
    }
    
    authService.verifyEmail({sptoken: $scope.sptoken}).then(function() {
        // TODO: I feel it would be better at this point to go straight into the consent process.
        // It's odd to be told you can sign in, then you can't sign in, you have to consent, then
        // you can sign in.
        $scope.messageType = "info";
        $scope.message = "Your email address has been verified. You can now sign in to Bridge and begin using the application.";
    }, function(data) {
        $scope.messageType = "danger";
        $scope.message = data.payload;
    });
    
}]);