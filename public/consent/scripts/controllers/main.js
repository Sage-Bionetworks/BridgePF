consent.controller('MainController', ['$scope', '$humane', '$window', '$http', 'formService',  
function($scope, $humane, $window, $http, formService) {
    
    $scope.setStep = function(num) {
        $scope.step = num;
        if ($scope.step === 3) {
            startMonitor();
        } else if ($scope.step === 4) {
            startAnimation("#step4");
        } else if ($scope.step === 5) {
            startAnimation("#step5");
        } else if ($scope.step === 7) {
            startAnimation("#step7");
        }
    };
    $scope.setStep(1);

    /* STEP 1 */
    /* -------------------------------------------------------------------- */
    
    /* STEP 2 */
    /* -------------------------------------------------------------------- */

    var step2buttons = [false, false, false, false],
        buttons = document.querySelectorAll("#step2 .image img");
    
    for (var i=0; i < buttons.length; i++) {
        var btn = buttons[i];
        btn.addEventListener("mousedown", press);
        btn.addEventListener("mouseout", release);
        if ('ontouchstart' in window) {
            btn.addEventListener("touchstart", press);
            btn.addEventListener("touchcancel", release);
        }
        new Image().src = btn.src.replace("_rest", "_press");
    }
    function press(event) {
        event.target.src = event.target.src.replace("_rest", "_press");
    }
    function release(event) {
        event.target.src = event.target.src.replace("_press", "_rest");
    }
    
    $scope.step2Assess = function(event, buttonNumber) {
        step2buttons[buttonNumber-1] = true;
        event.target.src = event.target.src.replace("_rest", "_selected");
        event.target.src = event.target.src.replace("_press", "_selected");
        
        if (step2buttons.every(function(s) { return s === true; })) {
            step2buttons = [false, false, false, false];
            setTimeout(function() {
                $scope.setStep(3);
                $scope.$apply();
            }, 500);
        }
    };

    /* STEP 3 */
    /* -------------------------------------------------------------------- */
    
    var monitorstate = 0,
        step3image = angular.element(document.querySelectorAll("#step3 .image > img")),
        step3footer = angular.element(document.querySelector("#step3 footer p"));
    
    function startMonitor() {
        window.addEventListener('deviceorientation', monitor, false);    
    }
    function flipOverOnMonitor() {
        monitorstate = 1;
        step3footer.addClass("flipped");
        step3footer.text("Turn your phone right side up to continue.");
        step3image.addClass("flipped");
    }
    function endMonitor() {
        monitorstate = 0;
        window.removeEventListener('deviceorientation', monitor);
        $scope.setStep(4);
        $scope.$apply();
    }
    function monitor(event) {
        $scope.$apply();
        if (event.beta < -70) {
            flipOverOnMonitor();
        } else if (monitorstate === 1 && event.beta > 70) {
            endMonitor();
        }
    }

    /* STEPS 4, 5, 7 (ANIMATION) */
    /* -------------------------------------------------------------------- */

    var animation_delay = 2000;
    
    function startAnimation(stepSelector) {
        var animStep = 0, elements = [], length = 0;
        addToElements(".info", ".image", "footer ");
        
        setTimeout(animate, animation_delay);

        function addToElements() {
            for (var i=0; i < arguments.length; i++) {
                var nl = document.body.querySelectorAll(stepSelector + " " + arguments[i] + ".animcell > *"),
                    array = [];
                for (var j=0; j < nl.length; j++) {
                    var element = nl[j],
                        frames = numFrames(element);
                    for (var k=0; k < frames; k++) {
                        array.push(element);
                    }
                }
                elements.push(array);
                if (array.length > length) {
                    length = array.length;
                }
            }
        }
        function numFrames(element) {
            if (element.getAttribute('data-frames')) {
                return parseInt(element.getAttribute('data-frames'), 10);
            }
            return 1;
        }
        function animate() {
            elements.forEach(function(nl) {
                nl[animStep].style.opacity = 0;
            });
            animStep++;
            elements.forEach(function(nl) {
                nl[animStep].style.opacity = 1;
            });
            if (animStep < (length-1)) {
                setTimeout(animate, animation_delay);
            }
        }
    }
    
    /* STEP 6 */
    /* -------------------------------------------------------------------- */

    /* STEP 8 */
    /* -------------------------------------------------------------------- */

    /* STEP 9 */
    /* -------------------------------------------------------------------- */
    
    formService.initScope($scope, 'consentForm');
    $scope.date = new Date().toLocaleString();
    
    $scope.submit = function() {
        $http.post('/api/auth/consentToResearch').then(function(response) {
            $scope.setStep(10);
        }, $humane.status);
    };
    
    $scope.decline = function() {
        $window.location.replace("/");
    };    
    
}]);
