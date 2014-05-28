bridge.service('loadingService', ['$rootScope', '$q', function($rootScope, $q) {
    return {
        call: function($http) {
            var deferred = $q.defer();
            $rootScope.loading++;
            $http.success(function(data, status) {
                $rootScope.loading--;
                data.status = status;
                deferred.resolve(data);
            }).error(function(data, status) {
                $rootScope.loading--;
                data.status = status;
                deferred.reject(data);
            });
            return deferred.promise;
        }
    };
}]);