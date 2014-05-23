bridge.controller('BloodPressureController', ['$scope', 'healthDataService', 'dashboardService', 
function($scope, healthDataService, dashboardService) {
    
    function midnight() {
        var date = new Date();
        date.setHours(0,0,0,0);
        return date;
    }
    
    // Ugly workaround for the fact that the form isn't available on the scope.
    // This is the simplest workaround I have found.
    $scope.setFormReference = function(bpForm) { $scope.bpForm = bpForm; };
    
    // somehow, this gets set as the default in the calendar control, go figure
    $scope.date = midnight(); 
    $scope.opened = false;
    $scope.format = 'MM/dd/yyyy';

    $scope.today = function() {
        $scope.bpForm.date.$setModelValue(midnight());
    };
    $scope.clear = function () {
        $scope.bpForm.date.$setModelValue(null);
    };
    // Disable after today
    $scope.disabled = function(date, mode) {
        return date.getTime() > new Date().getTime();
    };
    
    $scope.open = function(event) {
        $scope.opened = true; // contrary to docs, this does nothing
        setTimeout(function() { // however, this does work
            document.getElementById('datePicker').focus();    
        }, 1);
    };
    $scope.canSave = function() {
        // Have to test for presence of form because it's not immediately available,
        // because of transclusion weirdness.
        return ($scope.bpForm && $scope.bpForm.$dirty && $scope.bpForm.$valid);
    };
    $scope.save = function() {
        var payload = dashboardService.createPayload($scope.bpForm, ['date', 'date'], ['systolic', 'diastolic']);
        var chartScope = $scope.$parent;
        healthDataService.create(chartScope.tracker.id, payload).then(function(data) {
            payload.recordId = data.payload.ids[0];
            chartScope.dataset.convertOne(payload);
        }, function(data) {
            $humane.error(data.payload);
        });
        $scope.cancel();
    };
    $scope.cancel = function () {
        $scope.modalInstance.dismiss('cancel');
    };
    
}]);