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
        return (typeof message === "string") ? message : "There has been an error.";
    }
    
    function status(response) {
        console.log(response);
        if (arguments.length > 1) {
            // directly handling an $http error callback, assemble to something like a response object
            // 0 = data, 1 = status, 2 = headers, 3 = config, 4 = statusText
            response = {
                data: arguments[0], status: arguments[1], statusText: arguments[4]
            };
        }
        if (response.status !== 401) {
            var message = tryUntil(response, "data.message", "data", "message", "statusText");
            err.log(message);
        }
    }
    return {
        confirm: angular.bind(notifier, notifier.log),
        error: angular.bind(err, err.log),
        status: status
    };
}]);
