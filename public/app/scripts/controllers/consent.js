bridge.controller('ConsentController', ['$scope', '$http', '$location', '$humane', 'authService',   
function($scope, $http, $location, $humane, authService) {

    $scope.agree = function() {
        $http.post('/api/auth/consentToResearch').then(function(response) {
            authService.consented = true;
            $location.path("/");
        }, function(response) {
            $humane.error(response.data.payload);
        });
    };
    
    $scope.decline = function() {
        $location.path("/");
    };
    
}]);