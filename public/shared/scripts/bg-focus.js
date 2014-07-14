bridgeShared.directive('bgFocus', [function() {
    
    return {
        restrict: "A",
        link: function(scope, element, attrs, controller) {
            setTimeout(function() {
                element.focus();    
            }, 300);
        }
    };
    
}]);