bridgeShared.factory('loadingInterceptor', ['$q', '$injector', '$rootScope', function($q, $injector, $rootScope) {
    return {
        'request': function(config) {
            $rootScope.$broadcast('loadStart');
            return config;
        },
        'requestError': function(rejection) {
            $rootScope.$broadcast('loadEnd');
            return $q.reject(rejection);
        },
        'response': function(response) {
            $rootScope.$broadcast('loadEnd');
            return response;
        },
        'responseError': function(rejection) {
            $rootScope.$broadcast('loadEnd');
            return $q.reject(rejection);
        }
    };
}]);
bridgeShared.config(['$httpProvider', function($httpProvider) {
    $httpProvider.interceptors.push('loadingInterceptor');
}]);
