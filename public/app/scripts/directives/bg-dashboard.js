bridge.directive('bgDashboard', function() {
    return {
        restrict: 'E',
        controller: 'DashboardController',
        templateUrl: 'views/directives/dashboard.html',
        scope: {},
        link: function(scope, element, attrs, controller) {
            controller.init(element);
        }
    };
});