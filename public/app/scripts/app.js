var bridge = angular.module('bridge', ['ngRoute', 'ui.bootstrap'])
.run(['$rootScope', function($rootScope) {
    $rootScope.loading = 0;
}])
.config(['$provide', '$httpProvider', function($provide, $httpProvider) {
    $provide.factory('intercept401', ['$q', '$window', function($q, $window) {
        return {
            'responseError': function(rejection) {
                // TODO: But better would be if we stopped, showed a dialog, 
                // and allowed the user to log in again.
                if (rejection.status === 401) {
                    $window.location.replace("/");                    
                }
                return $q.reject(rejection, rejection.status);
            }
        };
    }]);
    $httpProvider.interceptors.push('intercept401');
}])
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
	.otherwise({ // the landing page, which is the research information page.
		templateUrl: '/views/research.html',
		controller: 'ResearchController',
		access: {allowAnonymous: true}
	});
}]);
