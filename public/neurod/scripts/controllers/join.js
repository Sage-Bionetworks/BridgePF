neurod.controller('JoinController', ['$scope', '$location', 'formService', 'authService', 
function($scope, $location, formService, authService) {

    formService.initScope($scope, 'signUpForm');
    
    $scope.join = function() {
        if ($scope.signUpForm.$valid) {
            var credentials = formService.formToJSON($scope.signUpForm, ['username', 'email', 'password']);
            $scope.message = '';
            authService.signUp(credentials).then(function() {
                $location.path('/joined?email='+credentials.email);
            }, function(response) {
                $scope.message = response.data.payload;
            });
        }
    };
    
}]);