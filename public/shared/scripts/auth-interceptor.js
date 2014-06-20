bridgeShared.factory('interceptAuth', ['$q', '$injector', '$location', function($q, $injector, $location) {
    return {
        'responseError': function(rejection) {
            if (rejection.status === 401) {
                var signInService = $injector.get('signInService');
                signInService.open(rejection.config);
            } else if (rejection.status === 412) {
                $location.path("/consent");
            }
            return $q.reject(rejection);
        }
    };
}]);
bridgeShared.config(['$httpProvider', function($httpProvider) {
    $httpProvider.interceptors.push('interceptAuth');
}]);
