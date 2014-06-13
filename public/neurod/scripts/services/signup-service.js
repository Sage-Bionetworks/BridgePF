module.service('signUpService', ['$modal', function($modal) {
    
    var modalInstance;

    var ModalInstanceController = ['$scope', '$humane', 'authService', 'formService', 
    function($scope, $humane, authService, formService) {

        formService.initScope($scope, 'signUpForm');
        
        $scope.signUp = function () {
            if ($scope.signUpForm.$valid) {
                var credentials = formService.formToJSON($scope.signUpForm, ['username', 'email', 'password']);
                $scope.message = '';
                authService.signUp(credentials).then(function() {
                    modalInstance.dismiss('cancel');
                    $humane.confirm("Please check your email for a message to verify your email address.");
                }, function(response) {
                    $scope.message = response.data.payload;
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
        }
    };
    return signUpService;
    
}]);