bridge.controller('DashboardController', ['$scope', '$humane', 'trackerService', function($scope, $humane, trackerService) {
    
    trackerService.getAll().then(function(response) {
        $scope.trackers = response.data.items;
    }, $humane.status);
    
    $scope.editTrackerList = function() {
        alert('Not implemented');
    };

}]);