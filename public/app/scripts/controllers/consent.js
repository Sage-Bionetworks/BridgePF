bridge.controller('ConsentController', ['$scope', '$http', '$window', '$location', '$humane', 'authService',   
function($scope, $http, $window, $location, $humane, authService) {

    $scope.agree = function() {
        $http.post('/api/auth/consentToResearch').then(function(response) {
            authService.consented = true;
            $location.path("/");
        }, $humane.status);
    };
    
    $scope.decline = function() {
        $window.location.replace("/");
    };
    
    $scope.begin = function() {
        $window.location.replace("/app/");
    };
    
}]);