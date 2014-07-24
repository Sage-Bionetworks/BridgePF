bridge.controller('UserDropdownController', ['$scope', function($scope) {

    $scope.signOut = function() {
        authService.signOut().then(function() {
            $window.location.replace("/");  
        }, function(response) {
            $humane.error(response.data.payload); 
        });
    };

    $scope.menuLinks = [
        {
            name: "Profile",
            imageHref: "/",
            href: "/"
        },
        {
            name: "Settings",
            imageHref: "/",
            href: "/"
        },
        {
            name: "Help",
            imageHref: "/",
            href: "/"
        },
        {
            name: "Log Out",
            imageHref: "/path/to",
            href: "/"
        }
    ];
}]);