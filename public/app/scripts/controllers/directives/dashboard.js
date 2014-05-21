bridge.controller('DashboardController', ['$scope', 'trackerService', 'dygraphService', '$humane', function($scope, trackerService, dygraphService, $humane) {
    
    trackerService.getAll().then(function(data) {
        $scope.trackers = data.payload;
    }, function(data) {
        $humane.error(data.payload);
    });
    
    $scope.editTrackerList = function() {
        alert('Not implemented');
    };

}]);