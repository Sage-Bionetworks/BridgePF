bridge.controller('ConsentController', ['$scope', '$http', '$location', '$route', '$humane', '$cookies',  
function($scope, $http, $location, $route, $humane, $cookies) {

    // TODO: I feel, if you include a header, it should take precedence over a cookie.
    // Currently it does not.
    $scope.sessionToken = $route.current.params.sessionToken;
    $cookies['Bridge-Session'] = $scope.sessionToken;
    
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