bridge.controller('ResetPasswordController', ['$scope', '$rootScope', '$route', '$http', '$humane', '$location', 'authService', 'formService',  
function($scope, $rootScope, $route, $http, $humane, $location, authService, formService) {
    
    authService.clear();
    
    $scope.sptoken = formService.retrieveSpToken($route);
    formService.initScope($scope, 'resetPasswordForm');
    
    $scope.submit = function() {
        authService.resetPassword($scope.password, $scope.sptoken).then(function() {
            $location.path("/");
            $humane.confirm("Your password has been changed.");
        }, function(response) {
            $humane.error(response.data.payload);
        });
    };
    
}]);