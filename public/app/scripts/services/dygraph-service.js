/**
 * Represents all the shared state that creates a dashboard and interaction
 * between the individual charts.
 */
bridge.service('dygraphService', function() {
    
    function dateFormatter(number, granularity, opts, dygraph) {
        return Dygraph.dateAxisFormatter(new Date(number), Dygraph.DAILY);
    }
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
    
    function DygraphService() {
        this.trackers = [];
        this.blockRedraw = false;
        this.xAxisOffset = 40;
        this.dateWindow = [new Date().getTime() - (14*24*60*60*1000), new Date().getTime()];
    }
    DygraphService.prototype = {
        options: function(target) {
            var opts = {
                highlightSeriesOpts: { strokeWidth: 2 },
                interactionModel: Dygraph.Interaction.dragIsPanInteractionModel,
                showLabelsOnHighlight: false,
                legend: 'never',
                showRoller: false,
                rollPeriod: 0,
                drawYAxis: false,
                drawXAxis: false,
                drawYGrid: false,
                showRangeSelector: false,
                drawCallback: redrawAll.bind(this),
                dateWindow: this.dateWindow,
                xRangePad: 0,
                errorBars: false, 
                // This might vary though because there's buggy behavior when the charts don't have data across the same timeframes
                panEdgeFraction: 1.0,
                // using millis allows us to use panEdgeFraction (doesn't work with dates), but then we have to format the date.
                axes: { x: { axisLabelFormatter: dateFormatter } }
            };          
            for (var prop in opts) {
                if (typeof target[prop] === "undefined") {
                    target[prop] = opts[prop];
                }
            }
            return target;
        },
        dateWindowControl: function(element) {
            var getData = function() {
                return [ [this.dateWindow[0], 0], [this.dateWindow[1], 0] ];
            }.bind(this);

            this.dateWindowGraph = new Dygraph(element, getData, this.options({
                showRangeSelector: true,
                rangeSelectorHeight: 30,
                height: 53,
                dateWindow: this.dateWindow,
                drawXAxis: true
            }));
        },
        createTimeSeriesChart: function(scope, element) {
            var originalData = scope.dataset.originalData;
            
            var hchandler = function(event, x, points, row, seriesName) {
                var record = originalData[x];
                
                // Making a tooltip, very exciting.
                /*
                makeTooltipForTimeSeries(event.target, x, {
                    title: new Date(data.date).toLocaleDateString(),
                    text: data.value
                });
                */
            };       
            
            var g = new Dygraph(element, function() { return scope.dataset.array; }, this.options({
                highlightSeriesOpts: false,
                labels: scope.dataset.labels,
                height: 170,
                width: element.offsetWidth-10,
                drawPoints: true,
                drawYAxis: true,
                yAxisLabelWidth: this.xAxisOffset,
                highlightCallback: hchandler
            }));  
            this.trackers.push({graph: g}); // why is graph called out here...
        }
    };
    return new DygraphService();
});