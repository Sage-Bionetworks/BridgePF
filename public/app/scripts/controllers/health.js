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
        for (var prop in record) {
            if (['recordId', 'startDate', 'endDate'].indexOf(prop) === -1) {
                object.data[prop] = record[prop];
            }
        }
        return object;
    }
    
    $scope.create = function() {
        if ($scope.record.diastolic && $scope.record.systolic && $scope.record.startDate) {
            
            var data = adjustValues($scope.record);
            
            healthDataService.create(1, 1, data).then(function(data, status) {
                $humane.confirm("Saved");
                updateTable();
            }, function(data, status) {
                $humane.error(data.payload);
            });
        }
    };
    $scope.remove = function(record) {
        healthDataService.remove(1, 1, record.recordId).then(function(data, status) {
            $humane.confirm("Deleted");
            updateTable();
        }, function(data, status) {
            $humane.error(data.payload);
        });
    };
    $scope.edit = function(record) {
        console.log($scope.record, record);
        $scope.record.systolic = record.data.systolic;
        $scope.record.diastolic = record.data.diastolic;
        $scope.record.startDate = new Date(record.startDate).toISOString().split("T")[0];
        $scope.record.recordId = record.recordId;
    };
    $scope.update = function(record) {
        if ($scope.record.diastolic && $scope.record.systolic && $scope.record.startDate) {
            
            var data = adjustValues($scope.record);
            
            healthDataService.update(1, 1, data).then(function(data, status) {
                $humane.confirm("Updated");
                updateTable();
            }, function(data, status) {
                $humane.error(data.payload);
            });
        }
    };

    $scope.records = [];
    updateTable();

    function updateTable() {
        healthDataService.getAll(1, 1).then(function(data, status) {
            $scope.records = data.payload.reverse();
        }, function(data,status) {
            $humane.error(data.payload);
        });
    }
}]);