bridge.service('requestResetPasswordService', ['$modal', function($modal) {
    
    var modalInstance;
    
    var ModalInstanceController = ['$scope', '$http', '$humane', 'formService', 
        function($scope, $http, $humane, formService) {
        
        formService.initScope($scope, 'requestResetPasswordForm');
        
        $scope.send = function () {
            $scope.sending = true;
            var json = formService.formToJSON($scope.requestResetPasswordForm, ['email']);
            $http.post('/api/auth/requestResetPassword', json).success(function(data) {
                $scope.sending = false;
                modalInstance.dismiss('cancel');
                $humane.confirm("Please look for further instructions in your email inbox.");
            }).error(function(data) {
                $scope.sending = false;
                $scope.setMessage(data.payload, 'danger');
            });
        };
        $scope.cancel = function () {
            modalInstance.dismiss('cancel');
        };
    }];
    
    return {
        open: function() {
            modalInstance = $modal.open({
                templateUrl: 'views/dialogs/requestResetPassword.html',
                controller: ModalInstanceController,
                size: 'sm',
                windowClass: 'sm'
            });
        },
        close: function() {
            if (modalInstance) {
                modalInstance.dismiss('cancel');
            }
        }
    };

}]);
