angular.module('bridge').service('RequestResetPasswordService', ['$modal', 'SessionService', function($modal, SessionService) {
    
    SessionService.clear();
    
    var modalInstance;
    
    var ModalInstanceController = function($scope, $http) {
        $scope.credentials = {email:''};
        $scope.messageType = "info";
        $scope.message = "";
        $scope.state = 'pre';

        $scope.send = function () {
            if ($scope.credentials.email === "") {
                $scope.messageType = "danger";
                $scope.message = "Please enter an email address.";
                return;
            }
            $http.post('/api/auth/requestResetPassword', {
                'email': $scope.credentials.email
            }).success(function(data) {
                $scope.messageType = "success";
                $scope.message = "Please look for further instructions in your email inbox.";
                $scope.state = 'post';
            }).error(function(data) {
                $scope.messageType = "danger";
                $scope.message = data.payload;
            });
        };
        $scope.cancel = function () {
            modalInstance.dismiss('cancel');
        };
    };
    
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
