angular.module('consent', ['bridge.shared']).controller('ConsentController', 
['$scope', '$humane', '$window', '$http', 'formService', 'learnMoreService', 
function($scope, $humane, $window, $http, formService, learnMoreService) {
    
    var mobileSteps = ["welcome", "tasks", "sensors", "deidentification", "aggregation", 
        "impact", "risk", "withdrawal", "consent", "thankyou"];
    
    var desktopSteps = ["welcome", "tasks", "sensorsDesktop", "deidentification", "aggregation", 
        "impact", "risk", "withdrawal", "consent", "thankyou"];
    
    var stepFunctions = {
        "welcome": angular.identity,
        "tasks": angular.identity,
        "sensors": startMonitor,
        "sensorsDesktop": angular.identity,
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
        var wiw = window.innerWidth;
        var steps = (wiw > 1024) ? desktopSteps : mobileSteps;
        document.body.className = (wiw > 1024) ? "desktop" : "mobile";
        
        var index = steps.indexOf($scope.step) + 1;
        if (steps[index]) {
            $scope.step = steps[index];
            $scope.setStep($scope.step);
        }
    };

    $scope.setStep("welcome");
    
    /* TASKS */
    /* -------------------------------------------------------------------- */

    var tasksButtons = [false, false, false, false];

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

    var animation_delay = 300;
    
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
    
    /* THANK YOU */
    /* -------------------------------------------------------------------- */
    
    $scope.begin = function() {
        $window.location.replace("/app/");
    };
    
    /* LEARN MORE*/
    /* -------------------------------------------------------------------- */
    
    $scope.learnMore = function(tag) {
        learnMoreService.open(tag);
    };

}]);
