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
}]);
