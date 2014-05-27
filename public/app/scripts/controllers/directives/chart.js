bridge.controller('ChartController', ['$scope', 'healthDataService', 'dashboardService', '$q', '$modal', 
function($scope, healthDataService, dashboardService, $q, $modal) {

    var trackerDataTypes = {
        "BloodPressure": TimeSeries,
        "Medication": EmptyDataSet
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
