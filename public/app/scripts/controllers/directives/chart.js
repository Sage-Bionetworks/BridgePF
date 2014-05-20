bridge.controller('ChartController', ['$scope', 'healthDataService', 'dygraphService', '$humane', function($scope, healthDataService, dygraphService, $humane) {

    var self = this;
    
    this.init = function(element) {
        self.element = element;
    };
    
    function convertTimeSeriesData(array) {
        array = array || [];
        var originalData = {};
        
        array.sort(function(a,b) {
            return a.startDate - b.startDate;
        });
        
        var labels = [];
        if (array.length) {
            labels.push('Date');
            for (var prop in array[0].data) {
                labels.push(prop);
            }
        }
        array = array.map(function(entry) {
            originalData[entry.startDate] = entry;
            
            var newArray = [entry.startDate];
            for (var prop in entry.data) {
                newArray.push(entry.data[prop]);
            }
            return newArray;
            
        });
        return {array: array, labels: labels, originalData: originalData};
    }
    
    /*
    function drawTimeSeries(context, tracker, dataset) {
        dataset = convertTimeSeriesData(dataset);
        
        var originalData = dataset.originalData;

        var hchandler = function(event, x, points, row, seriesName) {
            var data = originalData[x];
            makeTooltipForTimeSeries(event.target, x, {
                title: new Date(data.date).toLocaleDateString(),
                text: data.value
            });
        }
        var g = new Dygraph(tracker.graphDiv, function() { return dataset.array; }, context.options({
            highlightSeriesOpts: false,
            labels: dataset.labels,
            height: 170,
            width: $(tracker.graphDiv).width()-10,
            drawPoints: true,
            drawYAxis: true,
            yAxisLabelWidth: context.xAxisOffset,
            highlightCallback: hchandler,
        }));
        tracker.graph = g;
    }
     */
    
    // Scope stuff
    
    var startDate = new Date().getTime() - (14 * 24 * 60 * 60 * 1000);
    var endDate = new Date().getTime();
    
    $scope.records = [];
    $scope.options = function(tracker) {
        alert("Options not implemented for: " + tracker.name);
    };
    $scope.create = function(tracker) {
        alert("Create not implemented for: " + tracker.name);
    };
    
    healthDataService.getByDateRange($scope.tracker.id, startDate, endDate).then(function(data) {
        var dataset = convertTimeSeriesData(data.payload);
        
        if (dataset.array.length === 0) {
            return;
        }
        var root = self.element[0].querySelector(".gph div");

        var g = new Dygraph(root, function() { return dataset.array; }, dygraphService.options({
            highlightSeriesOpts: false,
            labels: dataset.labels,
            height: 170,
            width: root.offsetWidth-10,
            drawPoints: true,
            drawYAxis: true,
            yAxisLabelWidth: dygraphService.xAxisOffset/*,
            highlightCallback: hchandler,*/
        }));  
        dygraphService.trackers.push({graph: g});
    }, function(data) {
        $humane.error(data.payload);
    });

}]);
