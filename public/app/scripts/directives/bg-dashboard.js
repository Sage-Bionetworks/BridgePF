bridge.directive('bgDashboard', ['dygraphService', function(dygraphService) {
    return {
        restrict: 'E',
        controller: 'DashboardController',
        templateUrl: 'views/directives/dashboard.html',
        scope: {},
        link: function(scope, element, attrs, controller) {
            var div = element[0].querySelector(".rightcell div");
            dygraphService.dateWindowControl(div);
        }
    };
}]);