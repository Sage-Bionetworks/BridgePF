bridgeShared.directive('bgFocus', [function() {
    
    function makeFocuser(element) {
        return function() {
            element.focus();
        };
    }
    
    function focusElement(focuser) {
        try {
            setTimeout(focuser, 200);
        } catch(error) {
            focusElement(focuser);
        }
    }
    
    return {
        restrict: "A",
        link: function(scope, element, attrs, controller) {
            var focuser = makeFocuser(element[0]);
            focusElement(focuser);
        }
    };
    
}]);