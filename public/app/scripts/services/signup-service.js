bridge.service('signUpService', ['$modal', function($modal) {
    
    var modalInstance;
    
    var ModalInstanceController = ['$scope', '$humane', 'authService', 'formService', 
    function($scope, $humane, authService, formService) {

        formService.initScope($scope, 'signUpForm');
        
        $scope.signUp = function () {
            if ($scope.signUpForm.$valid) {
                var form = $scope.signUpForm;
                var credentials = formService.formToJSON($scope.signUpForm, ['username', 'email', 'password']);
                $scope.sending = true;
                $scope.message = '';
                authService.signUp(credentials).then(function() {
                    $scope.sending = false;
                    modalInstance.dismiss('cancel');
                    $humane.confirm("Please check your email for a message to verify your email address.");
                }, function(data) {
                    $scope.sending = false;
                    $scope.message = data.payload;
                });
            }
        };
        $scope.cancel = function () {
            modalInstance.dismiss('cancel');
            modalInstance = null;
        };
    }];
    var signUpService = {
        open: function(lr) {
            modalInstance = $modal.open({
                templateUrl: 'views/dialogs/signUp.html',
                controller: ModalInstanceController,
                size: 'lg',
                windowClass: 'lg'
            });
        },
        close: function() {
            if (modalInstance) {
                modalInstance.dismiss('cancel');
                modalInstance = null;
            }
        }
    };
    return signUpService;
    
}]);