bridge.service('$humane', ['$window', function($window) {
    var notifier = $window.humane.create({addnCls: "alert alert-success", timeout: 3000});
    var err = $window.humane.create({addnCls: "alert alert-danger", timeout: 3000});
    return {
        confirm: angular.bind(notifier, notifier.log),
        error: angular.bind(err, err.log)
    };
}]);
