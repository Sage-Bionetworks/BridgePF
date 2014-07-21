bridgeShared.directive('bgPressable', [function() {
    
    var images = [];
    
    // Creates a press state for buttons on mobile and desktop. This is different 
    // than :hover and should be the same as :active, but :active is hardly supported
    // on mobile. Grr.
    function press(event) {
        swapImage(event.target, "_rest", "_press");
    }
    function release(event) {
        swapImage(event.target, "_press", "_rest");
    }
    function swapImage(element, from, to) {
        element.src = element.src.replace(from, to);
    }
    function findPressable(element) {
        for (var n = element; n !== null; n = n.parentNode) {
            console.log(n);
            if (n.hasAttribute('bg-pressable')) {
                return n;
            }
        }
        return n;
    }
    function pressCss(to) {
        return function(event) {
            angular.element(findPressable(event.target)).addClass(to);
        };
    }
    function releaseCss(to) {
        return function(event) {
            angular.element(findPressable(event.target)).removeClass(to);
        };
    }
    
    return {
        restrict: "A",
        link: function(scope, element, attrs, controller) {
            if (element.attr('src')) {
                element.on("mousedown", press);
                element.on("mouseout mouseup", release);
                if ('ontouchstart' in window) {
                    element.on("touchstart", press);
                    element.on("touchend touchcancel", release);
                }
                // attempt to preload
                images.push(new Image().src = element[0].src.replace("_rest", "_press"));
            } else if (element.attr('bg-pressable')) {
                // In other words, the attribute had content we can use as an alternate class token
                var newClassName = element.attr('bg-pressable');
                element.on("mousedown", pressCss(newClassName));
                element.on("mouseout mouseup", releaseCss(newClassName));
                if ('ontouchstart' in window) {
                    element.on("touchstart", pressCss(newClassName));
                    element.on("touchend touchcancel", releaseCss(newClassName));
                }
                    
            } 
        }
    };
    
}]);
