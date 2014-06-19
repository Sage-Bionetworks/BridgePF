if ( !window.requestAnimationFrame ) {
    window.requestAnimationFrame = ( function() {
        return window.webkitRequestAnimationFrame ||
        window.mozRequestAnimationFrame ||
        window.oRequestAnimationFrame ||
        window.msRequestAnimationFrame ||
        function( /* function FrameRequestCallback */ callback, /* DOMElement Element */ element ) {
            window.setTimeout( callback, 1000 / 60 );
        };
    } )();
}

var neurod = angular.module('neurod', ['bridge.auth', 'ngRoute', 'ui.bootstrap']);

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

neurod.controller('NeurodController', ['$scope', 'signInService', 'requestResetPasswordService', 
function($scope, signInService, requestResetPasswordService) {
    $scope.signIn = function() {
        signInService.open();
    };
    $scope.resetPassword = function() {
        requestResetPasswordService.open();
    };
}]);

neurod.factory('loadingInterceptor', ['$q', '$injector', '$rootScope', function($q, $injector, $rootScope) {
    return {
        'request': function(config) {
            $rootScope.$broadcast('loadStart');
            return config;
        },
        'requestError': function(rejection) {
            $rootScope.$broadcast('loadEnd');
            return $q.reject(rejection);
        },
        'response': function(response) {
            $rootScope.$broadcast('loadEnd');
            return response;
        },
        'responseError': function(rejection) {
            $rootScope.$broadcast('loadEnd');
            return $q.reject(rejection);
        }
    };
}]);
neurod.config(['$httpProvider', function($httpProvider) {
    $httpProvider.interceptors.push('loadingInterceptor');
}]);