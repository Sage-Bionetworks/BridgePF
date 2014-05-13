angular.module('bridge').service('healthDataService', ['$http', '$rootScope', '$q', function($http, $rootScope, $q) {
    var service = {
        getAll: function(trackerId) {
            var deferred = $q.defer();
            var url = '/api/healthdata/'+trackerId;
            $rootScope.loading++;
            $http.get(url).success(function(data, status) {
                $rootScope.loading--;
                data.status = status;
                deferred.resolve(data);
            }).error(function(data, status) {
                $rootScope.loading--;
                data.status = status;
                deferred.reject(data);
            });
            return deferred.promise;
        },
        get: function(trackerId, recordId) {
            var deferred = $q.defer();
            var url = '/api/healthdata/'+trackerId+"/record/"+recordId;
            $rootScope.loading++;
            $http.get(url).success(function(data, status) {
                $rootScope.loading--;
                data.status = status;
                deferred.resolve(data);
            }).error(function(data, status) {
                $rootScope.loading--;
                data.status = status;
                deferred.reject(data);
            });
            return deferred.promise;
        },
        create: function(trackerId, object) {
            if (object.recordId) {
                throw new Error("Trying to create a record with a pre-existing recordId");
            }
            var deferred = $q.defer();
            var url = '/api/healthdata/'+trackerId;
            $rootScope.loading++;
            $http.post(url, JSON.stringify(object)).success(function(data, status) {
                $rootScope.loading--;
                data.status = status;
                deferred.resolve(data);
            }).error(function(data, status) {
                $rootScope.loading--;
                data.status = status;
                deferred.reject(data);
            });
            return deferred.promise;
        },
        update: function(trackerId, object) {
            if (!object.recordId) {
                throw new Error("Trying to update a record with no recordId");
            }
            var deferred = $q.defer();
            var url = '/api/healthdata/'+trackerId+'/record/'+object.recordId;
            $rootScope.loading++;
            $http.post(url, JSON.stringify(object)).success(function(data, status) {
                $rootScope.loading--;
                data.status = status;
                deferred.resolve(data);
            }).error(function(data, status) {
                $rootScope.loading--;
                data.status = status;
                deferred.reject(data);
            });
            return deferred.promise;
        },
        remove: function(trackerId, recordId) {
            var deferred = $q.defer();
            var url = '/api/healthdata/'+trackerId+'/record/'+recordId;
            $rootScope.loading++;
            $http.delete(url).success(function(data, status) {
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
    return service;
}]);