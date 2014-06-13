if ( !window.requestAnimationFrame ) {
    window.requestAnimationFrame = ( function() {
        return window.webkitRequestAnimationFrame ||
        window.mozRequestAnimationFrame ||
        window.oRequestAnimationFrame ||
        window.msRequestAnimationFrame ||
        function( /* function FrameRequestCallback */ callback, /* DOMElement Element */ element ) {
            window.setTimeout( callback, 1000 / 60 );
        };
    } )();
}

module.controller('ScrollController', ['$scope', function($scope) {
    var self = this;
    self.scrolled = false;
    
    function checkScroll() {
        if (self.scrolled) {
            self.children.removeClass('selected');

            for (var i=0; i < self.children.length; i++) {
                var child = self.children.eq(i);
                var selector = child.find("[data-for]").attr('data-for');
                var target = $("."+selector)[0];
                if (target && isScrolledIntoView(target)) {
                    $(child).addClass('selected');
                    break;
                }
            }
            self.scrolled = false;
        }
        requestAnimationFrame(checkScroll);
    }
    requestAnimationFrame(checkScroll);
    
    function isScrolledIntoView(elem) {
        elem = $(elem).find('h2').get(0) || elem;
        
        var docViewTop = $(window).scrollTop();
        var docViewBottom = docViewTop + $(window).height();
        var elemTop = $(elem).offset().top;
        var elemBottom = elemTop + $(elem).height();
        
        return ((elemBottom >= docViewTop) && (elemTop <= docViewBottom)
                && (elemBottom <= docViewBottom) &&  (elemTop >= docViewTop) );
    }
    
    self.init = function(element) {
        self.children = element.find("div");
    };
    
    $scope.scroll = function($event) {
        var className = $event.target.getAttribute("data-for");
        var offset = ($(".header").height() + $("nav").height()) * 1.1;
        var target = $("."+className);
        $("html,body").animate({scrollTop: target.offset().top - offset}, "slow");
    };
    
}]);
module.directive('bgScrollNav', ['$rootScope', function($rootScope) {
    
    $rootScope.$on('$routeChangeStart', function(e, next, current) {
        $("html,body").animate({scrollTop: 0}, "slow");
    });
    
    return {
        restrict: 'A',
        controller: 'ScrollController',
        link: function(scope, element, attrs, controller) {
            controller.init(element);
            
            $(window).scroll(function(event) {
                controller.scrolled = true;
            });
        }            
    };
    
}]);

