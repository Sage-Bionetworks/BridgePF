bridge.controller('ChartController', ['$scope', 'healthDataService', 'dygraphService', '$q', '$modal', 
function($scope, healthDataService, dygraphService, $q, $modal) {

    $scope.dataset = {array: [], labels: [], originalData: {}};
    
    var modalInstance, chartScope = $scope;
    
    // All of this stuff is very specific to the blood pressure form, and needs to be factored out.
    var ModalInstanceController = ['$scope', function($scope) {
        
        // Ugly workaround for the fact that the form isn't available on the scope.
        // This is the simplest workaround I have found.
        $scope.setFormReference = function(bpForm) { $scope.bpForm = bpForm; };
        
        // somehow, this gets set as the default in the calendar control, go figure
        $scope.date = new Date(); 
        $scope.opened = false;
        $scope.format = 'MM/dd/yyyy';
        $scope.dateOptions = {
            formatYear: 'yy',
            startingDay: 1
        };

        $scope.today = function() {
            $scope.bpForm.date.$setModelValue(new Date());
        };
        $scope.clear = function () {
            $scope.bpForm.date.$setModelValue(null);
        };
        // Disable after today
        $scope.disabled = function(date, mode) {
            return date.getTime() > new Date().getTime();
        };
        $scope.open = function($event) {
            $event.preventDefault();
            $event.stopPropagation();
            $scope.opened = true;
        };
        $scope.canSave = function() {
            // Have to test for presence of form because it's not immediately available,
            // because of transclusion wierdness.
            return ($scope.bpForm && $scope.bpForm.$dirty && $scope.bpForm.$valid);
        };
        $scope.save = function() {
            var payload = {
                startDate: $scope.bpForm.date.$modelValue.getTime(),
                endDate: $scope.bpForm.date.$modelValue.getTime(),
                data: {
                    systolic: $scope.bpForm.systolic.$modelValue,
                    diastolic: $scope.bpForm.diastolic.$modelValue,
                }
            };
            healthDataService.create(chartScope.tracker.id, payload).then(function(data) {
                // We want to update the graph now, ideally without going back
                // to the server again. This requires knowing about the chart, 
                // somehow.
                // You're going to push this item onto the end of the dataset. And the 
                // directive will notice this and update. Holy fuck this is a mess.
                payload.recordId = data.payload.ids[0];
                console.log("payload after save", payload);
                chartScope.dataset.array.push(addToTimeSeries(payload, chartScope.dataset.originalData));
                //chartScope.$apply();
            }, function(data) {
                $humane.error(data.payload);
            });
            $scope.cancel();
        };
        $scope.cancel = function () {
            modalInstance.dismiss('cancel');
        };
    }];
    
    $scope.options = function() {
        // Don't think this should be a modal dialog...
        /*
        modalInstance = $modal.open({
            templateUrl: 'views/dialogs/options.html',
            controller: ModalInstanceController
        });
        */
    };
    $scope.create = function() {
        var name = $scope.tracker.type.toLowerCase();
        modalInstance = $modal.open({
            templateUrl: 'views/trackers/'+name+'.html',
            controller: ModalInstanceController
        });
    };

    function addToTimeSeries(entry, originalData) {
        originalData[entry.startDate] = entry;
        console.log(entry.startDate);
        return [entry.startDate, entry.data.systolic, entry.data.diastolic];
    }
    
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
            return addToTimeSeries(entry, originalData);
            /*
            originalData[entry.startDate] = entry;
            return [entry.startDate, entry.data.systolic, entry.data.diastolic];
            */
        });
        return {array: array, labels: labels, originalData: originalData};
    }
    
    // Scope stuff
    
    this.load = function() {
        var deferred = $q.defer();
        var start = dygraphService.dateWindow[0];
        var end = dygraphService.dateWindow[1];
        
        // We want more data than the window. We want it to be possible for the user to scroll
        // back in time. Grab 2x the period and make that the date range for the data, but not the UI.
        start = start - ((end-start)*2);
        
        healthDataService.getByDateRange($scope.tracker.id, start, end).then(function(data, status) {
            $scope.dataset = convertTimeSeriesData(data.payload);
            deferred.resolve($scope.dataset);
        }, function(data, status) {
            deferred.reject(data);
        });
        return deferred.promise;
    };

}]);
