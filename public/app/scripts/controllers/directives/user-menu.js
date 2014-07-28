bridge.controller('UserMenuController', ['$window', '$humane', 'authService', 'modalService', 
    function($window, $humane, authService, modalService) {
    
    this.links = [
        {
            name: "Settings",
            id: "menu_settings",
            imageUrl: "/images/menu_settings_rest.svg",
            method: function() {
                modalService.openModal('views/settings.html');
            }
        },
        {
            name: "Help",
            id: "menu_help",
            imageUrl: "/images/menu_help_rest.svg",
            method: function() {
                // TO DO
            }
        },
        {
            name: "Log Out",
            id: "menu_logout",
            imageUrl: "/images/menu_logout_rest.svg",
            method: function() {
                authService.signOut().then(function() {
                    $window.location.replace("/");  
                }, function(response) {
                    $humane.error(response.data.payload); 
                });
            }
        }
    ];


}]);