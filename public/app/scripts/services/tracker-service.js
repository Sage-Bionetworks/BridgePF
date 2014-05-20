bridge.service('trackerService', ['$http', '$q', '$rootScope', function($http, $q, $rootScope) {
    
    // Duplicated from healthDataService and probably shared.
    function call($http) {
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

    var service = {
        getAll: function() {
            return call($http.get('/api/trackers'));
        },
        getSchema: function(trackerId) {
            return call($http.get('/api/trackers/schema/'+trackerId));
        }
    };
    return service;
}]);