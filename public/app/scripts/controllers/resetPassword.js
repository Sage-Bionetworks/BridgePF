angular.module('bridge').controller('ResetPasswordController', ['$scope', '$rootScope', '$route', '$http', '$humane', '$location', 'SessionService', 
function($scope, $rootScope, $route, $http, $humane, $location, SessionService) {
    
    // The URL from Synapse should be:
    // https://bridge.synapse.org/#/resetPassword/ + sessionToken
    // Change this at: ./services/repository-managers/src/main/java/org/sagebionetworks/repo/manager/MessageManagerImpl.java : 666

    SessionService.clear();
    $scope.sessionToken = $route.current.params.sessionToken;
    
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
            $http.post('/api/auth/resetPassword', {password: $scope.resetPasswordForm.password}, {
                headers: {'Bridge-Session': $scope.sessionToken}
            })
            .success(function(data, status) {
                $location.path("/");
                $humane.confirm("Your password has been changed");
            }).error(function(data, status) {
                $humane.error(data.payload);
            });
        }
    };
    
}]);