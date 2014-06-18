neurod.controller('BgCarouselController', ['$scope', function($scope) {
    var self = this;
    self.index = 0;
    
    function animateToDelta(delta) {
        self.index += delta;
        
        if (self.index < 0) {
            self.index = self.slides.length-1;
        } else if (self.index === self.slides.length) {
            self.index = 0;
        }
        self.slideElement.animate({left: -(self.width*self.index) + "px"});
    }
    $scope.prev = function() {
        animateToDelta(-1);
    };
    $scope.next = function() {
        animateToDelta(1);
    };
    this.init = function(element) {
        self.slides = $(element).find("li");
        
        var count = 0;
        self.images = $(element).find("img").load(function() {
            if (++count === self.images.length) {
                sizeCarousel();
            }
        }).error(function() {
            if (++count === self.images.length) {
                sizeCarousel();
                this.parentNode.removeChild(this);
            }
        });
    };
    $(window).resize(sizeCarousel);
    
    function sizeCarousel() {
        var first = self.slides.first();
        self.width = first.width()+40;

        var maxHeight = 0;
        self.slides.each(function(index, slide) {
            var slideHeight = $(slide).outerHeight(true);
            if (slideHeight > maxHeight) {
                maxHeight = slideHeight;
            }
        });
        
        self.slideElement = first.closest("ul").css({'height': maxHeight+"px", left: 0});
        
        var height = 0;
        for (var i=0; i < self.slides.length; i++) {
            var slide = self.slides.eq(i);
            slide.css({
                left: self.width*i+"px",
                top: -(height)+"px"
            });
            height += (slide.height()+5); // why
        }
    }
    
    
}])
.directive('bgCarousel', function() {
    
    return {
        restrict: 'E',
        replace: false,
        transclude: true,
        scope: {},
        templateUrl: 'neurod/views/carousel.html',
        controller: 'BgCarouselController',
        link: function(scope, element, attrs, controller) {
            controller.init(element);
        }
    };
});
