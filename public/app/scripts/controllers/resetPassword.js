bridge.controller('ResetPasswordController', ['$scope', '$rootScope', '$route', '$http', '$humane', '$location', 'authService', 'formService',  
function($scope, $rootScope, $route, $http, $humane, $location, authService, formService) {
    
    // route.params don't work here given the way stormpath structures the URL
    $scope.sptoken = $route.current.params.sptoken;
    if (!$scope.sptoken) {
        $scope.sptoken = (document.location.search+"").split("sptoken=")[1];    
    }
    
    authService.clear();
    formService.initScope($scope, 'resetPasswordForm');
    
    $scope.submit = function() {
        if ($scope.resetPasswordForm.$valid) {
            $http.post('/api/auth/resetPassword', {password: $scope.password, sptoken: $scope.sptoken}, {
                headers: {'Bridge-Session': $scope.sessionToken}
            })
            .success(function(data, status) {
                $location.path("/");
                $humane.confirm("Your password has been changed.");
            }).error(function(data, status) {
                $humane.error(data.payload);
            });
        }
    };
    
}]);