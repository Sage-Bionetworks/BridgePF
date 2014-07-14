if ( !window.requestAnimationFrame ) {
    window.requestAnimationFrame = ( function() {
        return window.webkitRequestAnimationFrame ||
        window.mozRequestAnimationFrame ||
        window.oRequestAnimationFrame ||
        window.msRequestAnimationFrame ||
        function(callback, element) {
            window.setTimeout(callback, 1000 / 60);
        };
    } )();
}

var neurod = angular.module('neurod', ['bridge.shared']);

neurod.config(['$routeProvider', function($routeProvider) {
    $routeProvider.when('/about', {
        templateUrl: '/neurod/views/about.html'
    })
    .when('/join', {
        templateUrl: '/neurod/views/join.html',
        controller: 'JoinController'
    })
    .when('/joined/:email', {
        templateUrl: '/neurod/views/joined.html',
        controller: 'JoinedController'
    })
    .otherwise({
        templateUrl: '/neurod/views/main.html'
    });
}]);
