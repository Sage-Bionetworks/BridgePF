bridgeShared.controller('RequestResetPasswordModalController', ['$scope', '$http', '$humane', '$modalInstance', 'formService', 
    function($scope, $http, $humane, $modalInstance, formService) {
    
    formService.initScope($scope, 'requestResetPasswordForm');
    
    $scope.send = function () {
        var json = formService.formToJSON($scope.requestResetPasswordForm, ['email']);
        $http.post('/api/auth/requestResetPassword', json).then(function() {
            $modalInstance.dismiss('cancel');
            $humane.confirm("Please look for further instructions in your email inbox.");
        }, function(response) {
            $scope.setMessage(response.data, 'danger');
        });
    };
    $scope.cancel = function () {
        $modalInstance.dismiss('cancel');
    };
    
}]);
