bridge.controller('SignUpModalController', ['$scope', '$humane', 'authService', 'formService','$modalInstance', 
    function($scope, $humane, authService, formService, $modalInstance) {

    formService.initScope($scope, 'signUpForm');
    
    $scope.signUp = function () {
        if ($scope.signUpForm.$valid) {
            var credentials = formService.formToJSON($scope.signUpForm, ['username', 'email', 'password']);
            $scope.message = '';
            $humane.confirm("Please check your email for a message to verify your email address.");
            authService.signUp(credentials).then(function() {
                modalInstance.dismiss('cancel');
                $humane.confirm("Please check your email for a message to verify your email address.");
            }, function(response) {
                $scope.message = response.data.payload;
            });
        }
    };
    $scope.cancel = function () {
        $modalInstance.dismiss('cancel');
    };
}]);
