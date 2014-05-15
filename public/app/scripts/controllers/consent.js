bridge.controller('ConsentController', ['$scope', '$http', '$location', '$route', '$humane', 
function($scope, $http, $location, $route, $humane) {

    $scope.sessionToken = $route.current.params.sessionToken;

    $scope.agree = function() {
        $http.post('/api/auth/consentToResearch', {}, {
            headers: {'Bridge-Session': $scope.sessionToken}
        })
        .success(function(data, status) {
            $location.path("/");
            $humane.confirm("Thank you for your participation! You can sign in now and get started.");
        }).error(function(data, status) {
            $humane.error(data.payload);
        });
    };
    
    $scope.decline = function() {
        $location.path("/");
    };
    
}]);