module.directive('bgCompare', function() {
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