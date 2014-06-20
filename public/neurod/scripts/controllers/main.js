neurod.controller('MainController', ['$scope', 'signInService', 'requestResetPasswordService', 
function($scope, signInService, requestResetPasswordService) {
    $scope.signIn = function() {
        signInService.open();
    };
    $scope.resetPassword = function() {
        requestResetPasswordService.open();
    };
}]);
