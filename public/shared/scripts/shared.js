var bridgeAuth = angular.module('bridge.auth', []);

bridgeAuth.run(['$location', '$humane', function($location, $humane) {
    var search = $location.search();
    switch(search.msg) {
    case 'passwordChanged':
        $humane.confirm("Your password has been changed."); break;
    }
}]);
