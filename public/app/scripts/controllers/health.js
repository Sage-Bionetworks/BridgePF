angular.module('bridge').controller('HealthController', ['$scope', '$http', '$humane', function($scope, $http, $humane) {
    $scope.data = {};
    
    $scope.create = function() {
        if ($scope.data.diastolic && $scope.data.systolic && $scope.data.startDate) {
            var data = {
                startDate: new Date($scope.data.startDate).getTime(),
                endDate: new Date($scope.data.startDate).getTime(),
                data: {
                    systolic: $scope.data.systolic,
                    diastolic: $scope.data.diastolic,
                }
            };
            
            $http.post('/api/healthdata/study/1/tracker/1', JSON.stringify(data)).success(function(data, status) {
                $humane.confirm("Saved");
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