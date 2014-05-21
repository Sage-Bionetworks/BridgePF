bridge.directive('bgChart', ['dygraphService', function(dygraphService) {
    
    function showChartIsEmpty(element) {
        element.innerHTML = '<div class="noChart">No data available for this tracker.</div>';
    }
    
    return {
        restrict: 'E',
        replace: true,
        templateUrl: 'views/directives/chart.html',
        controller: 'ChartController',
        scope: {
            'tracker': '='
        },
        link: function(scope, element, attrs, controller) {
            // TODO: With this logic in the directive, there's no way to update
            // it when the user adds a value. We're supposed to watch this property
            // and update accordingly...
            var root = element[0].querySelector(".gph div");
            // what we really want to do is watch the $scope.dataset property.
            // Can we do that?
            var promise = controller.load().then(function(data) {
                if (data.array.length === 0) {
                    showChartIsEmpty(root);
                } else {
                    dygraphService.createTimeSeriesChart(scope, root);    
                }
            }, function(data) {
                showChartIsEmpty(root);
            });
        }
    };
}]);