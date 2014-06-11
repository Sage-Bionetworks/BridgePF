var bridge = angular.module('bridge', ['ngRoute', 'ngCookies', 'ui.bootstrap'])
.config(['$routeProvider', function($routeProvider) {
	$routeProvider.when('/health/tracker/BloodPressure', {
	    templateUrl: '/views/trackers/bloodpressure.html',
	    controller: 'BloodPressureController',
	    access: {allowAnonymous: false}
	})
    .when('/health/tracker/Medication', {
        templateUrl: '/views/trackers/medication.html',
        controller: 'MedicationController',
        access: {allowAnonymous: false}
    })
    .when('/health', {
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
	.when('/resetPassword', {
        templateUrl: '/views/reset-password.html',
        controller: 'ResetPasswordController',
        access: {allowAnonymous: true}
	})
    .when('/verifyEmail', {
        templateUrl: '/views/verifyEmail.html',
        controller: 'VerifyEmailController',
        access: {allowAnonymous: true}
    })
    .when('/consent', {
        templateUrl: '/views/consent.html',
        controller: 'ConsentController',
        access: {allowAnonymous: false}
    })
	.otherwise({ // the landing page, which is the research information page.
		templateUrl: '/views/research.html',
		controller: 'ResearchController',
		access: {allowAnonymous: true}
	});
}]);
bridge.factory('loadingInterceptor', ['$q', '$injector', '$rootScope', function($q, $injector, $rootScope) {
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
bridge.config(['$httpProvider', function($httpProvider) {
    $httpProvider.interceptors.push('loadingInterceptor');
}]);