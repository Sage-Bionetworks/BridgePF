bridge.service('signInService', ['$modal', function($modal) {

    var modalInstance, lastRequest;

    var ModalInstanceController = ['$scope', '$location', '$http', '$route', 'authService', 
       function($scope, $location, $http, $route, authService) {
        
        $scope.credentials = {username: '', password: ''};

        $messageType = "error";
        $message = "Wrong user name or password.";
        $scope.signIn = function () {
            authService.signIn($scope.credentials).then(function() {
                modalInstance.dismiss('cancel');
                modalInstance = null;
                if (lastRequest) {
                    console.log("Resubmitting last request:", lastRequest);
                    var config = lastRequest;
                    lastRequest = null;
                    // This is all we can do at this point. We don't know the context to 
                    // update just one model based on the request. But nothing is lost 
                    // and you will see the updated.
                    $http(config).then($route.reload);
                }
            }, function(data) {
                if (data.status === 412) {
                    modalInstance.dismiss('cancel');
                    modalInstance = null;
                    $location.path("/consent/" + data.sessionToken);
                } else if (data.status === 404 || data.status === 401) {
                    $scope.messageType = "error";
                    $scope.message = "Wrong user name or password.";
                } else {
                    $scope.messageType = "error";
                    $scope.message = "There has been an error.";
                }
            });
            $scope.credentials.password = '';            
        };
        $scope.canSubmit = function() {
            return $scope.credentials.username && $scope.credentials.password;
        };
        $scope.cancel = function () {
            modalInstance.dismiss('cancel');
            modalInstance = null;
        };
    }];
    
    var signInService = {
        open: function(lr) {
            lastRequest = lr;
            modalInstance = $modal.open({
                templateUrl: 'views/dialogs/signIn.html',
                controller: ModalInstanceController,
                size: 'sm',
                windowClass: 'sm'
            });
        },
        close: function() {
            if (modalInstance) {
                modalInstance.dismiss('cancel');
                modalInstance = null;
            }
        }
    };
    return signInService;
}]);
bridge.factory('intercept401', ['$q', '$injector', function($q, $injector) {
    return {
        'responseError': function(rejection) {
            if (rejection.status === 401) {
                var signInService = $injector.get('signInService');
                signInService.open(rejection.config);
            }
            return $q.reject(rejection, rejection.status);
        }
    };
}]);
bridge.config(['$httpProvider', function($httpProvider) {
    $httpProvider.interceptors.push('intercept401');
}]);
