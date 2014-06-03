var bridge = angular.module('bridge', ['ngRoute', 'ui.bootstrap'])
.run(['$rootScope', function($rootScope) {
    $rootScope.loading = 0;
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
	.when('/resetPassword', {
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
