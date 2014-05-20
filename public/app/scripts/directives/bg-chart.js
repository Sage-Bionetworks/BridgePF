bridge.directive('bgChart', function() {
    return {
        restrict: 'E',
        replace: true,
        templateUrl: 'views/directives/chart.html',
        controller: 'ChartController',
        scope: {
            'tracker': '='
        },
        link: function(scope, element, attrs, controller) {
            controller.init(element);
        }
    };
});