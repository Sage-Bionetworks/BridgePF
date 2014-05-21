bridge.controller('BloodPressureController', ['$scope', 'healthDataService', function($scope, healthDataService) {
    
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
        var chartScope = $scope.$parent;
        healthDataService.create(chartScope.tracker.id, payload).then(function(data) {
            payload.recordId = data.payload.ids[0];
            chartScope.dataset.add(payload);
            //chartScope.dataset.array.push(addToTimeSeries(payload, chartScope.dataset.originalData));
        }, function(data) {
            $humane.error(data.payload);
        });
        $scope.cancel();
    };
    $scope.cancel = function () {
        $scope.modalInstance.dismiss('cancel');
    };
    
}]);