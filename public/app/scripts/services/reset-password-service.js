bridge.service('requestResetPasswordService', ['$modal', function($modal) {
    
    var modalInstance;
    
    var ModalInstanceController = ['$scope', '$http', '$humane', function($scope, $http, $humane) {
        $scope.messageType = "info";
        $scope.message = "";
        $scope.state = 'pre';
        $scope.credentials = {'email': ''};
        
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
            // TODO: This is not the Angular way, but forms in $modals do not show up in the scope,
            // this is something I need to sort out.
            return $scope.credentials.email && 
                  (/^[_a-z0-9-]+(\.[_a-z0-9-]+)*@[a-z0-9-]+(\.[a-z0-9-]+)*(\.[a-z]{2,4})$/).test($scope.credentials.email);
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
