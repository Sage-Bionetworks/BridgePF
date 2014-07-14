bridgeShared.service('signInService', ['$modal', 'requestResetPasswordService', function($modal, requestResetPasswordService) {

    var modalInstance = null, pendingRequests = [];

    var ModalInstanceController = ['$scope', '$window', '$http', '$route', '$q', 'authService', 'formService',  
       function($scope, $window, $http, $route, $q, authService, formService) {
        
        formService.initScope($scope, 'signInForm');

        $scope.signIn = function () {
            var credentials = formService.formToJSON($scope.signInForm, ['username', 'password']);
            $scope.signInForm.password.$setViewValue(null);
            $scope.signInForm.password.$render();

            authService.signIn(credentials).then(function(response) {
                $scope.cancel();
                authService.initSession(response.data.payload);

                if (!/\/app\//.test($window.location.pathname)) {
                    $window.location.replace("/app/");
                }
                if (pendingRequests.length) {
                    var requests = pendingRequests.map(function(config) {
                        return $http(config);
                    });
                    pendingRequests = [];
                    // This is all we can do at this point. We don't know the context to 
                    // update just one model based on the request. But nothing is lost 
                    // and you will see the updated.
                    $q.all(requests).then($route.reload);
                }
            }, function(response) {
                if (response.status === 412) {
                    $scope.cancel();
                    authService.initSession(response.data.payload);
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
        $scope.resetPassword = function() {
            $scope.cancel();
            requestResetPasswordService.open();
        };
    }];
    
    var signInService = {
        open: function(lastRequest) {
            if (lastRequest) {
                pendingRequests.push(lastRequest);    
            }
            if (modalInstance === null) {
                modalInstance = $modal.open({
                    templateUrl: '/shared/views/signIn.html',
                    controller: ModalInstanceController,
                    size: 'sm',
                    windowClass: 'sm'
                });
            }
        }
    };
    return signInService;
}]);
