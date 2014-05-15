bridge.service('requestResetPasswordService', ['$modal', function($modal) {
    
    var modalInstance;
    
    var ModalInstanceController = ['$scope', '$http', '$humane', function($scope, $http, $humane) {
        $scope.credentials = {email:''};
        $scope.messageType = "info";
        $scope.message = "";
        $scope.state = 'pre';

        $scope.send = function () {
            $http.post('/api/auth/requestResetPassword', {
                'email': $scope.credentials.email
            }).success(function(data) {
                modalInstance.dismiss('cancel');
                $humane.confirm("Please look for further instructions in your email inbox.");
                $scope.state = 'post';
            }).error(function(data) {
                $scope.messageType = "danger";
                $scope.message = data.payload;
            });
        };
        $scope.canSubmit = function() {
            return $scope.credentials.email;
        };
        $scope.cancel = function () {
            modalInstance.dismiss('cancel');
        };
    }];
    
    return {
        open: function() {
            modalInstance = $modal.open({
                templateUrl: 'views/dialogs/requestResetPassword.html',
                controller: ModalInstanceController
            });
        },
        close: function() {
            if (modalInstance) {
                modalInstance.dismiss('cancel');
            }
        }
    };

}]);
