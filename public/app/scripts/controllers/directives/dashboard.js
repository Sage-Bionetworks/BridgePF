bridge.controller('DashboardController', ['$scope', 'trackerService', '$humane', function($scope, trackerService, $humane) {
    
    trackerService.getAll().then(function(data) {
        $scope.trackers = data.payload;
    }, function(data) {
        $humane.error(data.payload);
    });
    
    $scope.editTrackerList = function() {
        alert('Not implemented');
    };

}]);