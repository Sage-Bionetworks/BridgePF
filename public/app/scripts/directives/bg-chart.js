bridge.directive('bgChart', ['dashboardService', function(dashboardService) {
    
    function showChartIsEmpty(element) {
        element.innerHTML = '<div class="noChart">No data available for this tracker.</div>';
    }
    function makeDataCallback(scope) {
        return function() {
            return scope.dataset.array;
        };
    }
    function createTimeSeriesChart(scope, element) {
        var originalData = scope.dataset.originalData;
        
        var hchandler = function(event, x, points, row, seriesName) {
            var record = originalData[x];
        };       
        var opts = dashboardService.options({
            highlightSeriesOpts: false,
            labels: scope.dataset.labels,
            height: 170,
            width: element.offsetWidth-10,
            drawPoints: true,
            yAxisLabelWidth: dashboardService.xAxisOffset,
            highlightCallback: hchandler,
            axes: { 
                y: { drawAxis: true, drawGrid: false },
                x: { drawAxis: false, axisLabelFormatter: dashboardService.dateFormatter }
            }
        });
        
        var g = new Dygraph(element, makeDataCallback(scope), opts);  
        dashboardService.trackers.push({graph: g}); // why is graph called out here...
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
            var root = element[0].querySelector(".gph div");
            
            scope.$watch(function() {
                return scope.dataset.array.length;
            }, function() {
                controller.load().then(function(data) {
                    if (data.array.length === 0) {
                        showChartIsEmpty(root);
                    } else {
                        createTimeSeriesChart(scope, root);    
                    }
                }, function(data) {
                    showChartIsEmpty(root);
                });
            });
        }
    };
}]);