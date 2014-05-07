angular.module('bridge').controller('HealthController', ['$scope', '$http', '$humane', function($scope, $http, $humane) {
    $scope.record = {};
    
    $scope.create = function() {
        if ($scope.record.diastolic && $scope.record.systolic && $scope.record.startDate) {
            var data = {
                startDate: new Date($scope.record.startDate).getTime(),
                endDate: new Date($scope.record.startDate).getTime(),
                data: {
                    systolic: $scope.record.systolic,
                    diastolic: $scope.record.diastolic,
                }
            };
            if ($scope.record.recordId) {
                data.recordId = $scope.record.recordId;
            }
            
            $http.post('/api/healthdata/study/1/tracker/1', JSON.stringify(data)).success(function(data, status) {
                $humane.confirm("Saved");
                updateTable();
            }).error(function(data, status) {
                $humane.error(data.payload);
            });
        }
    };
    $scope.remove = function(record) {
        $http.delete('/api/healthdata/study/1/tracker/1/record/'+record.recordId).success(function(data, status) {
            $humane.confirm("Deleted");
            updateTable();
        }).error(function(data, status) {
            $humane.error(data.payload);
        });
    };
    $scope.edit = function(record) {
        $scope.record.systolic = record.data.systolic;
        $scope.record.diastolic = record.data.diastolic;
        $scope.record.startDate = new Date(record.startDate).toISOString().split("T")[0];
        $scope.record.recordId = record.recordId;
    };
    $scope.update = function(record) {
        if ($scope.record.diastolic && $scope.record.systolic && $scope.record.startDate) {
            var data = {
                startDate: new Date($scope.record.startDate).getTime(),
                endDate: new Date($scope.record.startDate).getTime(),
                data: {
                    systolic: $scope.record.systolic,
                    diastolic: $scope.record.diastolic,
                }
            };
            if ($scope.record.recordId) {
                data.recordId = $scope.record.recordId;
            }
            $http.post('/api/healthdata/study/1/tracker/1/record/'+$scope.record.recordId, JSON.stringify(data)).success(function(data, status) {
                $humane.confirm("Updated");
                updateTable();
            }).error(function(data, status) {
                $humane.error(data.payload);
            });
        }
    };
    
    $scope.records = [];
    updateTable();
    
    function updateTable() {
        $http.get('/api/healthdata/study/1/tracker/1').success(function(data, status) {
            $scope.records = data.payload.reverse();
        }).error(function(data, status) {
            $humane.error(data.payload);
        });
    }
}]);