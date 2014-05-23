bridge.directive('bgChart', ['dashboardService', '$timeout', function(dashboardService, $timeout) {
    
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
    function findPos(obj) {
        var curleft = 0, curtop = 0;
        for (var o = obj; o !== null; o = o.offsetParent) {
            curleft += o.offsetLeft;
            curtop += o.offsetTop;
        }
        return [curleft,curtop];
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
            
            // The width of the popup effects where it should be on the X axis.
            var xy = findPos(event.target);

            var popover = element.parentNode.parentNode.querySelector(".popover");
            
            var style = window.getComputedStyle(popover);
            var width = parseFloat(style.width);
            
            popover.style.display = 'block';
            popover.style.left = Math.round((xy[0] - 34) + maxX) - width + "px";
            popover.style.top = Math.round(xy[1] + maxY + 10) + "px";
            
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
            
            controller.load().then(updateChart, function(data) {
                showChartIsEmpty(root);
            });
        }
    };
}]);