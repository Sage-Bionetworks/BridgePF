/**
 * Represents all the shared state that creates a dashboard and interaction
 * between the individual charts, as well as creating the Dygraphs. 
 */
bridge.service('dashboardService', ['$filter', '$q', 'healthDataService', function($filter, $q, healthDataService) {
    
    function redrawAll(me, initial) {
        if (this.blockRedraw || initial) return;
        this.blockRedraw = true;
        var range = me.xAxisRange();
        this.trackers.forEach(function(tracker) {
            if (!tracker.hidden && tracker.graph && tracker.graph !== me) {
                tracker.graph.updateOptions({ dateWindow: range });
            }
        });
        this.dateWindowGraph.updateOptions({ dateWindow: range });
        this.blockRedraw = false;
    }
    
    function DashboardService() {
        this.dateWindowGraph = null;
        this.trackers = [];
        this.blockRedraw = false;
        this.xAxisOffset = 40;
        var d = new Date().getTime();
        this.dateWindow = [d - (14*24*60*60*1000), d];
        this.dateFormatter = function(number, granularity, opts, dygraph) {
            return $filter('date')(new Date(number), 'M/d');
        };
    }
    DashboardService.prototype = {
        options: function(target) {
            var opts = {
                highlightSeriesOpts: { strokeWidth: 2 },
                interactionModel: Dygraph.Interaction.dragIsPanInteractionModel,
                showLabelsOnHighlight: false,
                legend: 'never',
                showRoller: false,
                rollPeriod: 0,
                showRangeSelector: false,
                drawCallback: redrawAll.bind(this),
                dateWindow: this.dateWindow,
                xRangePad: 0,
                errorBars: false, 
                // This might vary; there's buggy behavior when the charts don't have data across the same timeframes
                panEdgeFraction: 1.0 
            };          
            for (var prop in opts) {
                if (typeof target[prop] === "undefined") {
                    target[prop] = opts[prop];
                }
            }
            return target;
        },
        getDateWindowData: function() {
            return [ [this.dateWindow[0], 0], [this.dateWindow[1], 0] ];
        },
        makeDateControlWindow: function(element) {
            this.dateWindowGraph = new Dygraph(element, angular.bind(this, this.getDateWindowData), this.options({
                showRangeSelector: true,
                rangeSelectorHeight: 30,
                height: 53,
                labels: ['',''], // shut up warnings
                dateWindow: this.dateWindow,
                axes: { 
                    y: { drawAxis: false, drawGrid: false },
                    x: { drawAxis: true, axisLabelFormatter: this.dateFormatter }
                }
            }));
        }
    };
    return new DashboardService();
}]);