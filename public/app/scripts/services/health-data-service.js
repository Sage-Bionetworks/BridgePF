bridge.service('healthDataService', ['$http', '$rootScope', '$q', 'loadingService', function($http, $rootScope, $q, loadingService) {

    var service = {
        getAll: function(trackerId) {
            var url = '/api/healthdata/'+trackerId;
            return loadingService.call($http.get(url));
        },
        getByDateRange: function(trackerId, startDate, endDate) {
            var url = '/api/healthdata/'+trackerId+'/'+startDate+'/'+endDate;
            return loadingService.call($http.get(url));
        },
        get: function(trackerId, recordId) {
            var url = '/api/healthdata/'+trackerId+"/record/"+recordId;
            return loadingService.call($http.get(url));
        },
        create: function(trackerId, object) {
            if (object.recordId) {
                throw new Error("Trying to create a record with a pre-existing recordId");
            }
            var url = '/api/healthdata/'+trackerId;
            return loadingService.call($http.post(url, JSON.stringify([object])));
        },
        update: function(trackerId, object) {
            if (!object.recordId) {
                throw new Error("Trying to update a record with no recordId");
            }
            var url = '/api/healthdata/'+trackerId+'/record/'+object.recordId;
            return loadingService.call($http.post(url, JSON.stringify(object)));
        },
        remove: function(trackerId, recordId) {
            var url = '/api/healthdata/'+trackerId+'/record/'+recordId;
            return loadingService.call($http.delete(url));
        }
    };
    return service;
}]);