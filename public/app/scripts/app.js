angular.module('bridge', ['ngRoute', 'ui.bootstrap'])
.run(['$rootScope', function($rootScope) {
    $rootScope.loading = 0;
}])
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
	.otherwise({ // the landing page, which is the research information page.
		templateUrl: '/views/research.html',
		controller: 'ResearchController',
		access: {allowAnonymous: true}
	});
}])
.service('$humane', ['$window', function($window) {
    var notifier = $window.humane.create({addnCls: "alert alert-success", timeout: 3000});
    var err = $window.humane.create({addnCls: "alert alert-danger", timeout: 3000});
    return {
        confirm: angular.bind(notifier, notifier.log),
        error: angular.bind(err, err.log)
    };
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
});
