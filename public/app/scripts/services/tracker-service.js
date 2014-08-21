bridge.service('trackerService', ['$http', '$q', '$rootScope', function($http, $q, $rootScope) {
    var service = {
        getAll: function() {
            return $http.get('/api/v1/trackers');
        },
        getSchema: function(trackerId) {
            return $http.get('/api/v1/trackers/schema/'+trackerId);
        }
    };
    return service;
}]);