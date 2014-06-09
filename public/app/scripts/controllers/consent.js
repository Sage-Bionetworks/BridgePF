bridge.controller('ConsentController', ['$scope', '$http', '$location', '$route', '$humane', '$cookies',  
function($scope, $http, $location, $route, $humane, $cookies) {

    $scope.sessionToken = $route.current.params.sessionToken;
    $cookies['Bridge-Session'] = $scope.sessionToken;

    $scope.agree = function() {
        $http.post('/api/auth/consentToResearch', {}, {
            headers: {'Bridge-Session': $scope.sessionToken}
        }).then(function() {
            $location.path("/");
            $humane.confirm("Thank you for your participation! You can sign in now and get started.");
        }, function(response) {
            $humane.error(response.data.payload);
        });
    };
    
    $scope.decline = function() {
        $location.path("/");
    };
    
}]);