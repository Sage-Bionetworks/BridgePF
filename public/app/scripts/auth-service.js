angular.module('bridge').service('authService', ['$http', '$rootScope', '$location', '$window', '$humane',   
function($http, $rootScope, $location, $window, $humane) {
    var service = {
        sessionToken: '',
        username: '',
        authenticated: false,
        init: function(data) {
            $http.defaults.headers.common['Bridge-Session'] = data.sessionToken;
            this.sessionToken = data.sessionToken;
            this.username = data.username;
            this.authenticated = true;
        },
        clear: function() {
            delete $http.defaults.headers.common['Bridge-Session'];
            this.sessionToken = '';
            this.username = '';
            this.authenticated = false;
        },
        signIn: function(credentials) {
            if (!credentials.username && !credentials.password) {
                return;
            }
            credentials = angular.extend({}, credentials);
            
            $http.post('/api/auth/signIn', credentials).success(function(data, status) {
                service.init(data.payload);
            }).error(function(data, status) {
                if (status === 412) {
                    $location.path("/consent/" + data.sessionToken);
                } else if (status === 404 || status === 401) {
                    $humane.error("Wrong user name or password.");
                } else {
                    $humane.error("There has been an error.");
                }
            });
        },
        signOut: function(errorCallback) {
            $http.get('/api/auth/signOut').success(function(data, status) {
                $window.location.replace("/");
            }).error(function(data) {
                $humane.error(data.payload);
            });
        }
    };

    angular.extend(service, window.bridgeAuth);
    delete window.bridgeAuth;

    // Anonymous users can't access user routes.
    $rootScope.$on('$routeChangeStart', function(e, next, current) {
        if (!next.access.allowAnonymous && !service.authenticated) {
            console.warn("Page requires authentication, redirecting");
            e.preventDefault();
            $location.path("/");
        }
    });
    
    return service;
    
}]);