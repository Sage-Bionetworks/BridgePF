bridge.controller('BloodPressureController', ['$scope', 'healthDataService', '$humane',  
function($scope, healthDataService, $humane) {
    
    if ($scope.recordToEdit) {
        $scope.systolic = $scope.recordToEdit.data.systolic;
        $scope.diastolic = $scope.recordToEdit.data.diastolic;
        $scope.date = new Date($scope.recordToEdit.startDate);
    } else {
        // This is changed to midnight in createPayload() below
        $scope.date = new Date();
    }

    // Ugly workaround for the fact that the form isn't available on the scope.
    // This is the simplest workaround I have found.
    $scope.setFormReference = function(bpForm) { $scope.bpForm = bpForm; };
    
    // somehow, this gets set as the default in the calendar control, go figure
    $scope.opened = false;
    $scope.format = 'MM/dd/yyyy';

    $scope.today = function() {
        $scope.bpForm.date.$setModelValue(new Date().getTime());
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
        // because of transclusion weirdness.
        return ($scope.bpForm && $scope.bpForm.$dirty && $scope.bpForm.$valid);
    };
    $scope.canUpdate = function() {
        // Have to test for presence of form because it's not immediately available,
        // because of transclusion weirdness.
        return ($scope.bpForm && $scope.bpForm.$valid);
    };
    $scope.save = function() {
        var payload = healthDataService.createPayload($scope.bpForm, 
                                    ['date', 'date'], ['systolic', 'diastolic'], true);
        var chartScope = $scope.$parent;
        healthDataService.create(chartScope.tracker.id, payload).then(function(response) {
            payload.recordId = response.data.items[0].id;
            payload.version = response.data.items[0].version;
            chartScope.dataset.convertOne(payload);
        }, $humane.status);
        $scope.cancel();
    };
    $scope.update = function() {
        var payload = healthDataService.updateRecord($scope.recordToEdit, 
                $scope.bpForm, ['date', 'date'], ['systolic', 'diastolic']);
        delete payload.type;
        var chartScope = $scope.$parent;
        chartScope.dataset.update(payload);

        healthDataService.update(chartScope.tracker.id, payload).then(function(response) {
            $scope.recordToEdit.version = response.data.version;
        }, $humane.status);
        $scope.cancel();
    };
    $scope.cancel = function() {
        $scope.modalInstance.dismiss('cancel');
    };
    
}]);
