bridge.controller('ChartController', ['$scope', 'healthDataService', 'dashboardService', '$q', '$modal', 
function($scope, healthDataService, dashboardService, $q, $modal) {

    function TimeSeries() {
        this.array = [];
        this.labels = [];
        this.fields = [];
        this.originalData = {};
        this.changed = false;
    }
    TimeSeries.prototype = {
        convert: function(array) {
            this.array = [];
            this.originalData = {};
            if (array && array.length) {
                this.computeLabels(array[0]);
                for (var i=0, len = array.length; i < len; i++) {
                    this.add(array[i]);
                }
                this.recompute();
            }
            this.changed = true;
        },
        convertOne: function(entry) {
            if (entry) {
                this.computeLabels(entry);
                this.add(entry);
                this.recompute();
                this.changed = true;
            }
        },
        add: function(entry) {
            if (this.originalData[entry.startDate]) {
                this.originalData[entry.startDate].push(entry);
            } else {
                this.originalData[entry.startDate] = [entry];
            }
        },
        computeLabels: function(entry) {
            if (this.labels.length === 0) {
                this.labels.push('Date');
                for (var prop in entry.data) {
                    this.labels.push(prop);
                    this.fields.push(prop);
                }
            }
        },
        recompute: function() {
            function average(array, field) {
                return array.reduce(function(sum, entry) {
                    return sum + entry.data[field];
                }, 0) / array.length;
            }
            
            this.array = [];
            for (var time in this.originalData) {
                var series = this.originalData[time];
                
                var entry = [time];
                this.fields.forEach(function(field) {
                    entry.push(average(series, field));
                });
                this.array.push(entry);
            }
            this.array.sort(function(a,b) {
                return a[0] - b[0];
            });
        },
        remove: function(record) {
            for (var time in this.originalData) {
                var series = this.originalData[time];
                for (var i=0; i < series.length; i++) {
                    var r = series[i];
                    if (r.recordId === record.recordId) {
                        series.splice(i, 1);
                        this.recompute();
                        this.changed = true;
                        return;
                    }
                }
            }
        },
        update: function(record) {
            for (var time in this.originalData) {
                var series = this.originalData[time];
                for (var i=0; i < series.length; i++) {
                    var r = series[i];
                    if (r.recordId === record.recordId) {
                        series[i] = record;
                        this.recompute();
                        this.changed = true;
                        return;
                    }
                }
            }
        },
        hasChanged: function() {
            var c = this.changed;
            this.changed = false;
            return c;
        }
    };
    
    $scope.dataset = new TimeSeries();
    
    $scope.options = function(event, tracker) {
        event.target.parentNode.blur();
        alert("Not implemented");
    };
    $scope.createRecord = function() {
        $scope.recordToEdit = null;
        openModalEditor();
    };
    $scope.editRecord = function(record) {
        $scope.recordToEdit = record;
        openModalEditor();
    };
    $scope.removeRecord = function(record) {
        $scope.dataset.remove(record); 
        healthDataService.remove($scope.tracker.id, record.recordId);
    };
    
    function openModalEditor() {
        var name = $scope.tracker.type.toLowerCase();
        $scope.modalInstance = $modal.open({
            scope: $scope,
            templateUrl: 'views/trackers/'+name+'.html',
            controller: $scope.tracker.type + "Controller",
            size: 'sm', // doesn't work though, using .sm .modal-dialog CSS rules instead
            windowClass: 'sm'
        });
    }
}]);
