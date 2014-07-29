bridgeShared.service('requestResetPasswordService', ['$modal', function($modal) {
    
    var modalInstance;
    
    var ModalInstanceController = ['$scope', '$http', '$humane', 'formService', 
        function($scope, $http, $humane, formService) {
        
        formService.initScope($scope, 'requestResetPasswordForm');
        
        $scope.send = function () {
            var json = formService.formToJSON($scope.requestResetPasswordForm, ['email']);
            $http.post('/api/auth/requestResetPassword', json).then(function() {
                modalInstance.dismiss('cancel');
                $humane.confirm("Please look for further instructions in your email inbox.");
            }, function(response) {
                $scope.setMessage(response.data.payload, 'danger');
            });
        };
        $scope.cancel = function () {
            modalInstance.dismiss('cancel');
        };
    }];
    
    return {
        open: function() {
            modalInstance = $modal.open({
                templateUrl: '/shared/views/requestResetPassword.html',
                controller: ModalInstanceController,
                size: 'sm',
                windowClass: 'sm'
            });
        }
    };

}]);
