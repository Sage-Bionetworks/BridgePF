bridge.service('trackerService', ['$http', '$q', '$rootScope', 'loadingService', function($http, $q, $rootScope, loadingService) {
    var service = {
        getAll: function() {
            return loadingService.call($http.get('/api/trackers'));
        },
        getSchema: function(trackerId) {
            return loadingService.call($http.get('/api/trackers/schema/'+trackerId));
        }
    };
    return service;
}]);