bridgeAuth.service('$humane', ['$window', function($window) {
    var notifier = $window.humane.create({addnCls: "alert alert-success", timeout: 3000});
    var err = $window.humane.create({addnCls: "alert alert-danger", timeout: 3000});
    function status(response) {
        var message = (response.data.payload.message) ? response.data.payload.message : response.data.payload;
        if (response.status !== 401) {
            err.log(message);
        }
    }
    return {
        confirm: angular.bind(notifier, notifier.log),
        error: angular.bind(err, err.log),
        status: status
    };
}]);
