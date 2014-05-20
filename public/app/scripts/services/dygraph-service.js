bridge.service('dygraphService', function() {
    
    var self = this;
    
    function dateFormatter(number, granularity, opts, dygraph) {
        return Dygraph.dateAxisFormatter(new Date(number), Dygraph.DAILY);
    }
    function redrawAll(me, initial) {
        console.log(this);
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

    var dateWindow = [new Date().getTime() - (14*24*60*60*1000), new Date().getTime()];
    
    return {
        trackers: [],
        blockRedraw: false,
        xAxisOffset: 40,
        dateWindow: dateWindow,
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
                dateWindow: dateWindow,
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
        }
    };
});