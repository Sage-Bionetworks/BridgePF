bridge.controller('ChartController', ['$scope', 'healthDataService', 'dashboardService', '$q', '$modal', 
function($scope, healthDataService, dashboardService, $q, $modal) {

    function sortByStartDate(a,b) {
        return a.startDate - b.startDate;
    }
    
    function TimeSeries() {
        this.array = [];
        this.labels = [];
        this.originalData = {};
    }
    TimeSeries.prototype = {
        convert: function(array) {
            if (array && array.length) {
                this.createLabels(array[0]);
                for (var i=0, len = array.length; i < len; i++) {
                    this.add(array[i]);
                }
            }
            this.convert = function() {};
        },
        createLabels: function(entry) {
            if (this.labels.length === 0) {
                this.labels.push('Date');
                for (var prop in entry.data) {
                    this.labels.push(prop);
                }
            } else {
                console.log("Updating...");
            }
        },
        add: function(entry) {
            this.originalData[entry.startDate] = entry;
            this.array.push([entry.startDate, entry.data.systolic, entry.data.diastolic]);
            this.array.sort(sortByStartDate);
        }
    };
    
    $scope.dataset = new TimeSeries();

    /*
    $scope.dataset = {array: [], labels: [], originalData: {}};
    
    function addToTimeSeries(entry, originalData) {
        originalData[entry.startDate] = entry;
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
        });
        return {array: array, labels: labels, originalData: originalData};
    }
    */
    /*
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
    */
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
        $scope.modalInstance = $modal.open({
            scope: $scope,
            templateUrl: 'views/trackers/'+name+'.html',
            //controller: ModalInstanceController,
            controller: $scope.tracker.type + "Controller",
            size: 'sm', // doesn't work though, using .sm .modal-dialog CSS rules instead
            windowClass: 'sm'
        });
    };

    /*
    function addToTimeSeries(entry, originalData) {
        originalData[entry.startDate] = entry;
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
        });
        return {array: array, labels: labels, originalData: originalData};
    }
    */
    
    // Scope stuff
    
    this.load = function() {
        var deferred = $q.defer();
        var start = dashboardService.dateWindow[0];
        var end = dashboardService.dateWindow[1];
        
        // We want more data than the window. We want it to be possible for the user to scroll
        // back in time. Grab 2x the period and make that the date range for the data, but not the UI.
        start = start - ((end-start)*2);
        
        healthDataService.getByDateRange($scope.tracker.id, start, end).then(function(data, status) {
            $scope.dataset.convert(data.payload);
            //$scope.dataset = convertTimeSeriesData(data.payload);
            deferred.resolve($scope.dataset);
        }, function(data, status) {
            deferred.reject(data);
        });
        return deferred.promise;
    };

}]);
