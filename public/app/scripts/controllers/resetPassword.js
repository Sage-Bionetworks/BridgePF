bridge.controller('ResetPasswordController', ['$scope', '$rootScope', '$route', '$http', '$humane', '$location', 'authService', 
function($scope, $rootScope, $route, $http, $humane, $location, authService) {
    
    // Why doesn't routeParams work here?
    $scope.sptoken = $route.current.params.sptoken;
    if (!$scope.sptoken) {
        $scope.sptoken = (document.location.search+"").split("sptoken=")[1];    
    }
    
    authService.clear();

    $scope.hasErrors = function(model) {
        return {'has-error': model.$dirty && model.$invalid};
    };
    $scope.hasFieldError = function(model, type) {
        return model.$dirty && model.$error[type];
    };
    $scope.canChange = function() {
        var form = $scope.resetPasswordForm;
        return form.$dirty && form.$valid;
    };
    $scope.change = function() {
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