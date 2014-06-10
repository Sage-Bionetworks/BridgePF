bridge.service('signInService', ['$modal', function($modal) {

    var modalInstance, lastRequest;

    var ModalInstanceController = ['$scope', '$location', '$http', '$route', 'authService', 'formService',  
       function($scope, $location, $http, $route, authService, formService) {
        
        formService.initScope($scope, 'signInForm');

        $scope.signIn = function () {
            var credentials = formService.formToJSON($scope.signInForm, ['username', 'password']);
            $scope.signInForm.password.$setViewValue(null);
            $scope.signInForm.password.$render();

            authService.signIn(credentials).then(function(response) {
                authService.init(response.data.payload);
                $scope.cancel();
                if (lastRequest) {
                    console.log("Resubmitting last request:", lastRequest);
                    var config = lastRequest;
                    lastRequest = null;
                    // This is all we can do at this point. We don't know the context to 
                    // update just one model based on the request. But nothing is lost 
                    // and you will see the updated.
                    $http(config).then($route.reload);
                }
            }, function(response) {
                if (response.status === 412) {
                    $scope.cancel();
                    authService.handleConsent(response.data.payload);
                } else if (response.status === 404 || response.status === 401) {
                    $scope.setMessage("Wrong user name or password.", "danger");
                } else {
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
        open: function(lr) {
            lastRequest = lr;
            modalInstance = $modal.open({
                templateUrl: 'views/dialogs/signIn.html',
                controller: ModalInstanceController,
                size: 'sm',
                windowClass: 'sm'
            });
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
            return $q.reject(rejection);
        }
    };
}]);
bridge.config(['$httpProvider', function($httpProvider) {
    $httpProvider.interceptors.push('intercept401');
}]);
