bridge.controller('ResetPasswordController', ['$scope', '$rootScope', '$route', '$http', '$humane', '$location', 'authService', 
function($scope, $rootScope, $route, $http, $humane, $location, authService) {
    
    // Why doesn't routeParams work here?
    var sptoken = (document.location.search+"").split("sptoken=")[1];
    
    // TODO: The URL from Synapse should be:
    // https://bridge.synapse.org/#/resetPassword/ + sessionToken
    // Change this at: ./services/repository-managers/src/main/java/org/sagebionetworks/repo/manager/MessageManagerImpl.java : 666
    
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
            $http.post('/api/auth/resetPassword', {password: $scope.password, sptoken: sptoken})
            .success(function(data, status) {
                $location.path("/");
                $humane.confirm("Your password has been changed.");
            }).error(function(data, status) {
                $humane.error(data.payload);
            });
        }
    };
    
}]);