neurod.controller('MainController', ['$scope', 'signInService', function($scope, signInService) {
    $scope.signIn = function() {
        signInService.open();
    };
}]);
