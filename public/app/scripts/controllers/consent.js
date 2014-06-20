bridge.controller('ConsentController', ['$scope', '$http', '$window', '$location', '$humane', 'authService',   
function($scope, $http, $window, $location, $humane, authService) {

    $scope.agree = function() {
        $http.post('/api/auth/consentToResearch').then(function(response) {
            authService.consented = true;
            $location.path("/");
        }, function(response) {
            $humane.error(response.data.payload);
        });
    };
    
    $scope.decline = function() {
        $window.location.replace("/");
    };
    
}]);