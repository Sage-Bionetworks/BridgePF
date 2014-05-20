bridge.controller('DashboardController', ['$scope', 'trackerService', 'dygraphService', '$humane', function($scope, trackerService, dygraphService, $humane) {
    
    this.init = function(element) {
        dateWindowControl(element[0], dygraphService.dateWindow);
    };
    
    trackerService.getAll().then(function(data) {
        $scope.trackers = data.payload;
    }, function(data) {
        $humane.error(data.payload);
    });
    
    $scope.editTrackerList = function() {
        alert('Not implemented');
    };

    function dateWindowControl(element, dateWindow) {
        function getData() {
            return [ [dateWindow[0], 0], [dateWindow[1], 0] ];
        }
        
        var div = element.querySelector(".rightcell div");

        dygraphService.dateWindowGraph = new Dygraph(div, getData, dygraphService.options({
            showRangeSelector: true,
            rangeSelectorHeight: 30,
            height: 53,
            dateWindow: dateWindow,
            drawXAxis: true
        }));
    }

}]);