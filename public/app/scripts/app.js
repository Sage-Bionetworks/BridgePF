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
.directive('validateEquals', function() {
    return {
        restrict: 'A',
        require: 'ngModel',
        link: function(scope, element, attrs, controller) {
            function getComparisonValue(expr) {
                var comparisonModel = scope.$eval(expr);
                return (comparisonModel && comparisonModel.$viewValue) ? comparisonModel.$viewValue : undefined;
            }
            // http://stackoverflow.com/questions/20982751/custom-form-validation-directive-to-compare-two-fields
            var validate = function(viewValue) {
                var comparisonModel = getComparisonValue(attrs.validateEquals);

                var valid = true;
                if (!viewValue || !comparisonModel) {
                    controller.$setValidity('equal', true);
                } else {
                    valid = (viewValue === comparisonModel);
                    controller.$setValidity('equal', valid);
                }
                return (valid) ? viewValue : undefined;
            };
            
            controller.$parsers.unshift(validate);
            controller.$formatters.push(validate);
            // This really doesn't seem to do anything.
            /*
            scope.$watch(attrs.validateEquals, function(comparisonModel) {
                if (controller.$dirty) {
                    controller.$setViewValue(controller.$viewValue);    
                }
            });
            */
        }
    };
});
