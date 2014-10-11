angular.module('bridge.shared').factory('interceptAuth', function($q, $injector, $location) {
    return {
        'responseError': function(rejection) {
            if (rejection.status === 401) {
                var signInService = $injector.get('signInService');
                signInService.open(rejection.config);
            } else if (rejection.status === 412) {
                $location.path("/consent");
            }
            return $q.reject(rejection);
        }
    };
});

var test = angular.module('test', ['bridge.shared']);

test.config(['$routeProvider', function($routeProvider) {
    $routeProvider.when('/joined/:email', {
        templateUrl: '/test/views/joined.html',
        controller: 'JoinedController'
    })
    .when('/verifyEmail', {
        templateUrl: '/test/views/verify-email.html',
        controller: 'VerifyEmailController'
    })
    .when('/consent', {
        templateUrl: '/test/views/consent.html',
        controller: "ConsentController"
    })
    .otherwise({
        templateUrl: '/test/views/main.html',
        controller: 'MainController'
    });
}])
.controller('MainController', function($scope, $location, formService, authService, signInService) {

    formService.initScope($scope, 'signUpForm');

    $scope.submit = function() {
        if ($scope.signUpForm.$valid) {
            var credentials = formService.formToJSON($scope.signUpForm, ['username', 'email', 'password']);
            $scope.message = '';
            authService.signUp(credentials).then(function() {
                $location.path('/joined/'+credentials.email);
            }, function(response) {
                $scope.message = response.data.message;
            });
        }
    };
    
    $scope.signIn = function() {
        signInService.open();
    };
    
    $scope.requestResetPassword = function() {
        $location.path('/requestResetPassword');
    };

})
.controller('JoinedController', function($scope, $routeParams) {
    $scope.email = $routeParams.email;
})
.controller('VerifyEmailController', function($scope, $route, $location, $http, $humane, authService, formService) {

    $scope.setMessage("Verifying...");
    
    var sptoken = formService.retrieveSpToken($route);
    authService.verifyEmail({sptoken: sptoken}).then(function(response) {
        $scope.setMessage("Your email address has been verified. Thank you!");
    }, function(response) {
        if (response.status === 412) {
            authService.initSession(response.data);
            $location.path("/consent");
        } else {
            $scope.setMessage(response.data.message, "danger");
        }
    });
    
})
.controller('ConsentController', function($scope, $http, $humane, $routeParams, formService, authService) {
    formService.initScope($scope, 'consentForm');

    $scope.consented = authService.consented;
    
    $scope.date = new Date();

    function focusName() {
        setTimeout(function() {
            document.querySelector("input[name='name']").focus();    
        }, 200);
    }
    
    $scope.disabled = function(date, mode) {
        return date.getTime() > $scope.date.getTime();
    };

    $scope.open = function($event) {
        $event.preventDefault();
        $event.stopPropagation();
        $scope.opened = true;
    };

    $scope.dateOptions = {
        formatYear : 'yyyy',
        startingDay : 1
    };
    
    $scope.maxDate = 18;

    $scope.submit = function() {
        var consent = formService.formToJSON($scope.consentForm, ['name', 'birthdate']);
        consent.birthdate = consent.birthdate.toISOString().split('T')[0];

        $http.post('/api/v1/consent', consent).then(function(response) {
            authService.consented = $scope.consented = true;
        }, $humane.status);
    };
   
});
