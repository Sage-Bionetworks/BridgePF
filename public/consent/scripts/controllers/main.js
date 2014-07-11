consent.controller('MainController', ['$scope', '$humane', '$window', '$http', 'formService',     
function($scope, $humane, $window, $http, formService) {
    
    var steps = ["welcome", "tasks", "sensors", "deidentification", "aggregation", 
                 "impact", "risk", "withdrawal", "consent", "thankyou"];
    
    var stepFunctions = {
        "welcome": angular.identity,
        "tasks": angular.identity,
        "sensors": startMonitor,
        "deidentification": startAnimation,
        "aggregation": startAnimation,
        "impact": angular.identity,
        "risk": startAnimation,
        "withdrawal": angular.identity,
        "consent": angular.identity,
        "thankyou": angular.identity
    };
    
    $scope.setStep = function(stepName) {
        $scope.step = stepName;
        stepFunctions[stepName](stepName);
        _gaq.push(['_trackPageview', '/consent/'+stepName]);
    };
    
    $scope.nextStep = function() {
        var index = steps.indexOf($scope.step) + 1;
        if (steps[index]) {
            $scope.step = steps[index];
            $scope.setStep($scope.step);
        }
    };

    //$scope.setStep("welcome");
    $scope.setStep("deidentification");
    
    /* TASKS */
    /* -------------------------------------------------------------------- */

    var tasksButtons = [false, false, false, false],
        buttons = document.querySelectorAll("#tasks .image img");
    
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
        swapImage(event.target, "_rest", "_press");
    }
    function release(event) {
        swapImage(event.target, "_press", "_rest");
    }
    function swapImage(element, from, to) {
        element.src = element.src.replace(from, to);
    }
    
    $scope.assessTask = function(event, buttonNumber) {
        tasksButtons[buttonNumber-1] = true;
        swapImage(event.target, "_rest", "_selected");
        swapImage(event.target, "_press", "_selected");
        
        if (tasksButtons.every(function(s) { return s === true; })) {
            tasksButtons = [false, false, false, false];
            setTimeout(function() {
                $scope.nextStep();
                $scope.$apply();
            }, 500);
        }
    };

    /* SENSORS */
    /* -------------------------------------------------------------------- */
    
    var monitorstate = 0,
        sensorsImage = angular.element(document.querySelectorAll("#sensors .image > img")),
        sensorsFooter = angular.element(document.querySelector("#sensors footer p"));
    
    function startMonitor() {
        window.addEventListener('deviceorientation', monitor, false);    
    }
    function flipOverOnMonitor() {
        monitorstate = 1;
        sensorsFooter.addClass("flipped");
        sensorsFooter.text("Turn your phone right side up to continue.");
        sensorsImage.addClass("flipped");
    }
    function endMonitor() {
        monitorstate = 0;
        window.removeEventListener('deviceorientation', monitor);
        $scope.nextStep();
        $scope.$apply();
    }
    function monitor(event) {
        if (event.beta < -70) {
            flipOverOnMonitor();
        } else if (monitorstate === 1 && event.beta > 70) {
            endMonitor();
        }
    }

    /* DE-IDENTIFICATION, AGGREGATION, RISK ANIMATION */
    /* -------------------------------------------------------------------- */

    var animation_delay = 750;
    
    function startAnimation(stepSelector) {
        var animStep = 0, elements = [], length = 0;
        addToElements(".info", ".image", "footer ");
        
        setTimeout(animate, animation_delay);

        function addToElements() {
            for (var i=0; i < arguments.length; i++) {
                var selector = "#"+stepSelector+" "+arguments[i]+".animcell > *",
                    nl = document.body.querySelectorAll(selector),
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
    function numFrames(element) {
        if (element.getAttribute('data-frames')) {
            return parseInt(element.getAttribute('data-frames'), 10);
        }
        return 1;
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
            $scope.nextStep();
        }, $humane.status);
    };
    
    $scope.decline = function() {
        $window.location.replace("/");
    };    
    
}]);
