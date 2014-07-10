var bridge = angular.module('bridge', ['bridge.shared'])
.config(['$routeProvider', function($routeProvider) {
	$routeProvider.when('/health/tracker/BloodPressure', {
	    templateUrl: 'views/trackers/bloodpressure.html',
	    controller: 'BloodPressureController'
	})
    .when('/health/tracker/Medication', {
        templateUrl: 'views/trackers/medication.html',
        controller: 'MedicationController'
    })
    .when('/health', {
        templateUrl: 'views/health.html',
        controller: 'HealthController'
    })
	.when('/questions', {
		templateUrl: 'views/questions.html',
		controller: 'QuestionsController'
	})
	.when('/journal', {
		templateUrl: 'views/journal.html',
		controller: 'JournalController'
	})
	.when('/resetPassword', {
        templateUrl: 'views/reset-password.html',
        controller: 'ResetPasswordController'
	})
    .when('/verifyEmail', {
        templateUrl: 'views/verifyEmail.html',
        controller: 'VerifyEmailController'
    })
    .when('/consent', {
        templateUrl: 'views/consent.html',
        controller: 'ConsentController'
    })
	.otherwise({
		templateUrl: 'views/research.html',
		controller: 'ResearchController'
	});
}]);
