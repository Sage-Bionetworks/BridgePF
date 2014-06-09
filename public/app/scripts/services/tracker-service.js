bridge.service('trackerService', ['$http', '$q', '$rootScope', function($http, $q, $rootScope) {
    var service = {
        getAll: function() {
            return $http.get('/api/trackers');
        },
        getSchema: function(trackerId) {
            return $http.get('/api/trackers/schema/'+trackerId);
        }
    };
    return service;
}]);