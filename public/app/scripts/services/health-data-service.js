bridge.service('healthDataService', ['$http', '$rootScope', '$q', function($http, $rootScope, $q) {
    
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
        getAll: function(trackerId) {
            var url = '/api/healthdata/'+trackerId;
            return call($http.get(url));
        },
        get: function(trackerId, recordId) {
            var url = '/api/healthdata/'+trackerId+"/record/"+recordId;
            return call($http.get(url));
        },
        create: function(trackerId, object) {
            if (object.recordId) {
                throw new Error("Trying to create a record with a pre-existing recordId");
            }
            var url = '/api/healthdata/'+trackerId;
            return call($http.post(url, JSON.stringify([object])));
        },
        update: function(trackerId, object) {
            if (!object.recordId) {
                throw new Error("Trying to update a record with no recordId");
            }
            var url = '/api/healthdata/'+trackerId+'/record/'+object.recordId;
            return call($http.post(url, JSON.stringify(object)));
        },
        remove: function(trackerId, recordId) {
            var url = '/api/healthdata/'+trackerId+'/record/'+recordId;
            return call($http.delete(url));
        }
    };
    return service;
}]);