bridge.controller('UserMenuController', ['$humane', '$window', 'authService', 'modalService',
    function($humane, $window, authService, modalService) {
    
    this.links = [
        {
            name: "Settings",
            id: "menu_settings",
            imageUrl: "/images/menu_settings_rest.svg",
            method: function() {
                modalService.openModal('SettingsModalController', 'lg', 'views/settings.html');
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
            name: "Sign Out",
            id: "signOutLink",
            imageUrl: "/images/menu_logout_rest.svg",
            method: function() {
                authService.signOut().then(function() {
                    $window.location.replace("/");
                }, $humane.status);
            }
        }
    ];


}]);