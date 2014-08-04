bridge.directive('bgUserMenu', function() {

    return {
        restrict: 'E',
        controller: 'UserMenuController',
        controllerAs: 'menu',
        templateUrl: 'views/directives/user-menu.html',
    };

});