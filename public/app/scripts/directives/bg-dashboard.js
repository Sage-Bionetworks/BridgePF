bridge.directive('bgDashboard', ['dashboardService', function(dashboardService) {
    
    // TODO: So, is this just here because it's UI? 
    // Everything here defers to dashboardService 
    
    function getData() {
        return [ [dashboardService.dateWindow[0], 0], [dashboardService.dateWindow[1], 0] ];
    }
    function makeDateWindowControl(element) {
        return new Dygraph(element, getData, dashboardService.options({
            showRangeSelector: true,
            rangeSelectorHeight: 30,
            height: 53,
            labels: ['',''], // shut up warnings
            dateWindow: dashboardService.dateWindow,
            axes: { 
                y: { drawAxis: false, drawGrid: false },
                x: { drawAxis: true, axisLabelFormatter: dashboardService.dateFormatter }
            }
        }));
    }
    return {
        restrict: 'E',
        controller: 'DashboardController',
        templateUrl: 'views/directives/dashboard.html',
        scope: {},
        link: function(scope, element, attrs, controller) {
            var div = element[0].querySelector(".rightcell div");
            dashboardService.dateWindowGraph = makeDateWindowControl(div);
        }
    };
}]);