bridge.directive('bgChart', ['dashboardService', '$timeout', function(dashboardService, $timeout) {
    
    // This data is all specific to a time series chart, so bg-chart.js is not the right place for it.
    // Need to consolidate this down.
    
    function showChartIsEmpty(element) {
        element.innerHTML = '<div class="noChart">No data available for this tracker.</div>';
        // @style is put there by Dygraphs and can look funny when you delete the last data point
        element.removeAttribute('style'); 
    }
    function makeDataCallback(scope) {
        return function() {
            return scope.dataset.array;
        };
    }
    function createTimeSeriesChart(scope, element) {
        var originalData = scope.dataset.originalData;
        
        var timer = null;
        
        function startTimer() {
            if (timer !== null) {
                $timeout.cancel(timer);
                timer = null;
            }
            var popover = element.parentNode.parentNode.querySelector(".popover");
            timer = $timeout(function() {
                popover.style.display = 'none';    
            }, 1000);
        }
        
        var hchandler = function(event, x, points, row, seriesName) {
            
            $timeout.cancel(timer);
            
            var maxX = points[0].canvasx;
            var maxY = Math.max.apply(null, points.map(function(point) {
                return point.canvasy;
            }));
            var array = originalData[x];
            scope.$apply(function() {
                scope.xDate = x;
                scope.records = array;
            });
            
            var popover = element.parentNode.parentNode.querySelector(".popover");
            var style = window.getComputedStyle(popover);
            var width = parseFloat(style.width);
            popover.style.display = 'block';
            popover.style.left = maxX - (width/2) + "px";
            popover.style.top = (maxY + 15) + "px";
            
            if (!popover.__processed) {
                popover.__processed = true;
                popover.addEventListener('mouseover', function() {
                    $timeout.cancel(timer);
                }, false);
                popover.addEventListener('mouseout', startTimer, false);
            }
        };
        
        var opts = dashboardService.options({
            highlightSeriesOpts: false,
            labels: scope.dataset.labels,
            height: 170,
            width: element.offsetWidth-10,
            drawPoints: true,
            yAxisLabelWidth: dashboardService.xAxisOffset,
            highlightCallback: hchandler,
            unhighlightCallback: startTimer,
            axes: { 
                y: { drawAxis: true, drawGrid: false },
                x: { drawAxis: false, axisLabelFormatter: dashboardService.dateFormatter }
            }
        });
        
        var graph = new Dygraph(element, makeDataCallback(scope), opts);
        dashboardService.trackers.push({graph: graph}); // why is graph called out here...
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
            
            function updateChart() {
                if (scope.dataset.array.length === 0) {
                    showChartIsEmpty(root);
                } else {
                    createTimeSeriesChart(scope, root);
                }
            }
            scope.$watch(function() {
                return scope.dataset.hasChanged();
            }, updateChart);

            controller.refreshChartFromServer().then(updateChart);
        }
    };
}]);