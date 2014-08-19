bridgeShared.service('$humane', ['$window', function($window) {
    var notifier = $window.humane.create({addnCls: "alert alert-success", timeout: 3000});
    var err = $window.humane.create({addnCls: "alert alert-danger", timeout: 3000});
    
    function tryUntil(obj) {
        var message = null, i=1;
        while (!message && i < arguments.length) {
            try {
                message = new Function("value", "return value."+arguments[i++]+";")(obj);
            } catch(e) {
            }
        }
        return message;
    }
    
    function status(response) {
        if (response.status !== 401) {
            var message = tryUntil(response, "data.message", "data", "message", "statusText");
            if (typeof message !== "string") {
                message = "There has been an error.";
            }
            err.log(message);
        }
    }
    return {
        confirm: angular.bind(notifier, notifier.log),
        error: angular.bind(err, err.log),
        status: status
    };
}]);
