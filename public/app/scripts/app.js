angular.module('bridge', ['ngRoute', 'ui.bootstrap'])

.config(['$routeProvider', function($routeProvider) {
	$routeProvider.when('/health', {
		templateUrl: '/views/health.html',
		controller: 'HealthController',
		access: {allowAnonymous: false}
	})
	.when('/questions', {
		templateUrl: '/views/questions.html',
		controller: 'QuestionsController',
		access: {allowAnonymous: false}
	})
	.when('/journal', {
		templateUrl: '/views/journal.html',
		controller: 'JournalController',
		access: {allowAnonymous: false}
	})
	.when('/resetPassword/:sessionToken', {
        templateUrl: '/views/resetPassword.html',
        controller: 'ResetPasswordController',
        access: {allowAnonymous: true}
	})
    .when('/consent/:sessionToken', {
        templateUrl: '/views/consent.html',
        controller: 'ConsentController',
        access: {allowAnonymous: true}
    })
	// As a default, you see the landing page, which is the research information page.
	// Eventually this can vary by project.
	.otherwise({
		templateUrl: '/views/research.html',
		controller: 'ResearchController',
		access: {allowAnonymous: true}
	});
}])
.service('SessionService', ['$http', '$rootScope', function($http, $rootScope) {
	var service = {
		sessionToken: '',
		username: '',
		authenticated: false,
		init: function(data) {
			$http.defaults.headers.common['Bridge-Session'] = data.sessionToken;
			this.sessionToken = data.sessionToken;
			this.username = data.username;
			this.authenticated = true;
			$rootScope.$broadcast('session', this);
		},
		clear: function() {
			delete $http.defaults.headers.common['Bridge-Session'];
			this.sessionToken = '';
			this.username = '';
			this.authenticated = false;
			$rootScope.$broadcast('session', this);
		}
	};

	angular.extend(service, window.auth);
	delete window.auth;

	return service;
}])
.service('$humane', function() {
    var hmn = humane.create({addnCls: "alert alert-success", timeout: 3000});
    var err = hmn.spawn({addnCls: 'alert alert-danger'});
    return {
        confirm: function(s) { hmn.log(s); },
        error: function(s) { err(s); } 
    };
})
.directive('validateEquals', function() {
    return {
        restrict: 'A',
        require: 'ngModel',
        link: function(scope, element, attrs, ngModelController) {
            function validateEqual(myValue) {
                var valid = (myValue === scope.$eval(attrs.validateEquals).$modelValue);
                ngModelController.$setValidity('equal', valid);
                return valid ? myValue : undefined;
            }
            ngModelController.$parsers.push(validateEqual);
            ngModelController.$formatters.push(validateEqual);
            
            // Change in model 
            /*
            scope.$watch(attrs.validateEquals, function() {
                ngModelController.$setViewValue(ngModelController.$viewValue);
            });
            */
        }
    };
});
