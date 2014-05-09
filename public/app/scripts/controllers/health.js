angular.module('bridge').controller('HealthController', ['$scope', '$humane', 'healthDataService', function($scope, $humane, healthDataService) {
    $scope.record = {data:{}};
    
    function adjustValues(record) {
        var object = {data: {}};
        if (record.recordId) { 
            object.recordId = record.recordId; 
        }
        if (record.startDate) {
            object.startDate = new Date(record.startDate).getTime();
        } else {
            object.startDate = new Date().getTime();
        }
        if (record.endDate) {
            object.endDate = new Date(record.endDate).getTime();
        } else {
            object.endDate = object.startDate;
        }
        for (var prop in record.data) {
            object.data[prop] = record.data[prop];
        }
        return object;
    }
    
    $scope.create = function() {
        if ($scope.record.data.diastolic && $scope.record.data.systolic && $scope.record.startDate) {
            var data = adjustValues($scope.record);
            
            healthDataService.create(1, 1, data).then(function(data) {
                $humane.confirm("Saved");
                updateTable();
            }, function(data) {
                $humane.error(data.payload);
            });
        }
    };
    $scope.remove = function(record) {
        healthDataService.remove(1, 1, record.recordId).then(function(data) {
            $humane.confirm("Deleted");
            updateTable();
        }, function(data) {
            $humane.error(data.payload);
        });
    };
    $scope.edit = function(record) {
        $scope.record.recordId = record.recordId;
        $scope.record.startDate = new Date(record.startDate).toISOString().split("T")[0];
        $scope.record.endDate = new Date(record.endDate).toISOString().split("T")[0];
        $scope.record.data.systolic = record.data.systolic;
        $scope.record.data.diastolic = record.data.diastolic;
    };
    $scope.update = function(record) {
        if ($scope.record.data.diastolic && $scope.record.data.systolic && $scope.record.startDate) {
            
            var data = adjustValues($scope.record);
            
            healthDataService.update(1, 1, data).then(function(data) {
                $humane.confirm("Updated");
                updateTable();
            }, function(data) {
                $humane.error(data.payload);
            });
        }
    };

    $scope.records = [];
    updateTable();

    function updateTable() {
        healthDataService.getAll(1, 1).then(function(data) {
            $scope.records = data.payload.reverse();
        }, function(data) {
            $humane.error(data.payload);
        });
    }
}]);