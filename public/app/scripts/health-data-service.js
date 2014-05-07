angular.module('bridge').service('healthDataService', ['$http', '$q', function($http,   $q) {
    var service = {
        getAll: function(studyId, trackerId) {
            var deferred = $q.defer();
            var url = '/api/healthdata/study/'+studyId+'/tracker/'+trackerId;
            $http.get(url).success(function(data, status) {
                data.status = status;
                deferred.resolve(data);
            }).error(function(data, status) {
                data.status = status;
                deferred.reject(data);
            });
            return deferred.promise;
        },
        get: function(studyId, trackerId, recordId) {
            var deferred = $q.defer();
            var url = '/api/healthdata/study/'+studyId+'/tracker/'+trackerId+"/record/"+recordId;
            $http.get(url).success(function(data, status) {
                data.status = status;
                deferred.resolve(data);
            }).error(function(data, status) {
                data.status = status;
                deferred.reject(data);
            });
            return deferred.promise;
        },
        create: function(studyId, trackerId, object) {
            if (object.recordId) {
                throw new Error("Trying to create a record with a pre-existing recordId");
            }
            var deferred = $q.defer();
            var url = '/api/healthdata/study/'+studyId+'/tracker/'+trackerId;
            $http.post(url, JSON.stringify(object)).success(function(data, status) {
                data.status = status;
                deferred.resolve(data);
            }).error(function(data, status) {
                data.status = status;
                deferred.reject(data);
            });
            return deferred.promise;
        },
        update: function(studyId, trackerId, object) {
            if (!object.recordId) {
                throw new Error("Trying to update a record with no recordId");
            }
            var deferred = $q.defer();
            var url = '/api/healthdata/study/'+studyId+'/tracker/'+trackerId+'/record/'+object.recordId;
            $http.post(url, JSON.stringify(object)).success(function(data, status) {
                data.status = status;
                deferred.resolve(data);
            }).error(function(data, status) {
                data.status = status;
                deferred.reject(data);
            });
            return deferred.promise;
        },
        remove: function(studyId, trackerId, recordId) {
            var deferred = $q.defer();
            var url = '/api/healthdata/study/'+studyId+'/tracker/'+trackerId+'/record/'+recordId;
            $http.delete(url).success(function(data, status) {
                data.status = status;
                deferred.resolve(data);
            }).error(function(data, status) {
                data.status = status;
                deferred.reject(data);
            });
            return deferred.promise;
        }
    };
    return service;
}]);