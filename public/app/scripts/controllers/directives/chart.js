bridge.controller('ChartController', ['$scope', 'healthDataService', 'dashboardService', '$q', '$modal', 
function($scope, healthDataService, dashboardService, $q, $modal) {

    var trackerDataTypes = {
        "BloodPressure": TimeSeries,
        "Medication": EmptyDataSet
    };
    
    this.refreshChartFromServer = function() {
        var deferred = $q.defer();
        var start = dashboardService.dateWindow[0];
        var end = dashboardService.dateWindow[1];
        // We want more data than the window. We want it to be possible for the user to scroll
        // back in time. Grab 2x the period and make that the date range for the data, but not the UI.
        start = start - ((end-start)*2);

        healthDataService.getByDateRange($scope.tracker.id, start, end).then(function(data, status) {
            $scope.dataset.convert(data.payload);
            deferred.resolve($scope.dataset);
        }, function(data, status) {
            $scope.dataset.clear();
            deferred.reject(data);
        });
        return deferred.promise;
    };
    
    $scope.dataset = new trackerDataTypes[$scope.tracker.type]();
    
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
