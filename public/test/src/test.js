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
.directive('bgCompare', function() {
    function compareFunc(controller, field1, field2) {
        return function(e) {
            if (field1.$dirty && field2.$dirty) {
                var value1 = field1.$viewValue;
                var value2 = field2.$viewValue;
                controller.$setValidity('equal', value1 === value2);
            }
        };
    }
    return {
        restrict: 'A',
        require: 'form',
        link: function(scope, element, attrs, controller) {
            var fieldNames = attrs.bgCompare.split(",").map(function(s) { return s.trim(); });

            var field1 = controller[fieldNames[0]];
            var field2 = controller[fieldNames[1]];
            scope.$watch(fieldNames[0], compareFunc(controller, field1, field2));
            scope.$watch(fieldNames[1], compareFunc(controller, field1, field2));
        }
    };
})
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
    var y100 = $scope.date.getTime() - (100*365*24*60*60*1000); 

    function focusName() {
        setTimeout(function() {
            document.querySelector("input[name='name']").focus();    
        }, 200);
    }
    $scope.disabled = function(date, mode) {
        // It'll let you go all the way back to 20CE so you have to set a boundary there.
        return !(date.getTime() > y100 && date.getTime() < $scope.date.getTime());
    };

    $scope.format = 'dd/MM/yyyy';
    
    $scope.open = function($event) {
        $event.preventDefault();
        $event.stopPropagation();
        $scope.opened = true;
    };

    $scope.submit = function() {
        var consent = formService.formToJSON($scope.consentForm, ['name', 'birthdate']);
        consent.birthdate = consent.birthdate.toISOString().split('T')[0];
        
        $http.post('/api/v1/consent', consent).then(function(response) {
            authService.consented = $scope.consented = true;
        }, $humane.status);
    };
   
});
