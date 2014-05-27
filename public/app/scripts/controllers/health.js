bridge.controller('HealthController', ['$scope', '$humane', '$http', 
function($scope, $humane, $http) {

    $scope.trackers = [];

    $http.get('/api/trackers').success(function(data, status) {
        $scope.trackers = data;
    }).error(function(data, status) {
        $humane.error(data.payload);
    });
    
}]);