bridge.controller('HealthController', ['authService', function(authService) {
    
    if (!authService.consented) {
        authService.handleConsent();
    }
    
    // Nothing on the health screen exists but the dashboard, at this point
    
}]);
