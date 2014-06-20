var bridgeShared = angular.module('bridge.shared', []);

bridgeShared.run(['$location', '$humane', function($location, $humane) {
    var search = $location.search();
    switch(search.msg) {
    case 'passwordChanged':
        $humane.confirm("Your password has been changed."); break;
    }
}]);
