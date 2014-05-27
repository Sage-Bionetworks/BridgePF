bridge.directive('bgDashboard', ['dashboardService', function(dashboardService) {
    
    return {
        restrict: 'E',
        controller: 'DashboardController',
        templateUrl: 'views/directives/dashboard.html',
        scope: {},
        link: function(scope, element, attrs, controller) {
            var div = element[0].querySelector(".rightcell div");
            dashboardService.makeDateControlWindow(div);
        }
    };
}]);