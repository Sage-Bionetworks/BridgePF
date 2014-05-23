/**
 * Represents all the shared state that creates a dashboard and interaction
 * between the individual charts.
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
        this.dateWindow = [new Date().getTime() - (14*24*60*60*1000), new Date().getTime()];
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
        createPayload: function(form, dateFields, fields) {
            var payload = {
                startDate: form[dateFields[0]].$modelValue.getTime(),
                endDate: form[dateFields[1]].$modelValue.getTime(),
                data: {}
            };
            fields.forEach(function(field) {
                payload.data[field] = form[field].$modelValue;
            });
            return payload;
        },
        updateRecord: function(record, form, dateFields, fields) {
            record.startDate = form[dateFields[0]].$modelValue.getTime();
            record.endDate = form[dateFields[1]].$modelValue.getTime();
            fields.forEach(function(field) {
                record.data[field] = form[field].$modelValue;
            });
            delete record.$$hashKey; // oh Angular
            return record;
        },
        refreshChartFromServer: function(chartScope) {
            var deferred = $q.defer();
            var start = this.dateWindow[0];
            var end = this.dateWindow[1];
            // We want more data than the window. We want it to be possible for the user to scroll
            // back in time. Grab 2x the period and make that the date range for the data, but not the UI.
            start = start - ((end-start)*2);

            healthDataService.getByDateRange(chartScope.tracker.id, start, end).then(function(data, status) {
                chartScope.dataset.convert(data.payload);
                deferred.resolve(chartScope.dataset);
            }, function(data, status) {
                deferred.reject(data);
            });
            return deferred.promise;
        }
    };
    return new DashboardService();
}]);