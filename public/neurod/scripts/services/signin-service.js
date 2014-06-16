module.service('signInService', ['$modal', function($modal) {

    var modalInstance;

    var ModalInstanceController = ['$scope', '$window', '$http', '$route', 'authService', 'formService',  
       function($scope, $window, $http, $route, authService, formService) {
        
        formService.initScope($scope, 'signInForm');

        $scope.signIn = function () {
            var credentials = formService.formToJSON($scope.signInForm, ['username', 'password']);
            $scope.signInForm.password.$setViewValue(null);
            $scope.signInForm.password.$render();

            authService.signIn(credentials).then(function(response) {
                authService.init(response.data.payload);
                $scope.cancel();
                $window.location.replace("/app/");
            }, function(response) {
                if (response.status === 412) {
                    $scope.cancel();
                    authService.handleConsent(response.data.payload);
                } else if (response.status === 404 || response.status === 401) {
                    $scope.setMessage("Wrong user name or password.", "danger");
                } else {
                    console.error(response.data.payload);
                    $scope.setMessage("There has been an error.", "danger");
                }
            });
        };
        $scope.cancel = function () {
            modalInstance.dismiss('cancel');
            modalInstance = null;
        };
    }];
    
    var signInService = {
        open: function() {
            modalInstance = $modal.open({
                templateUrl: 'app/views/dialogs/signIn.html',
                controller: ModalInstanceController,
                size: 'sm',
                windowClass: 'sm'
            });
        }
    };
    return signInService;
}]);
