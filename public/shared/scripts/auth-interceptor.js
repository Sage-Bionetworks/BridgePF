bridgeShared.factory('interceptAuth', ['$q', '$injector', '$window', function($q, $injector, $window) {
    return {
        'responseError': function(rejection) {
            if (rejection.status === 401) {
                var signInService = $injector.get('signInService');
                signInService.open(rejection.config);
            } else if (rejection.status === 412) {
                $window.location.replace("/consent/"+rejection.data.sessionToken);
            }
            return $q.reject(rejection);
        }
    };
}]);
bridgeShared.config(['$httpProvider', function($httpProvider) {
    $httpProvider.interceptors.push('interceptAuth');
}]);
