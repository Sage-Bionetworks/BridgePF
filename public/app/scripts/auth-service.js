angular.module('bridge').service('authService', ['$http', '$rootScope', '$location', '$window', '$humane', '$q',    
function($http, $rootScope, $location, $window, $humane, $q) {
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
            
            var deferred = $q.defer();
            $http.post('/api/auth/signIn', credentials).success(function(data, status) {
                service.init(data.payload);
                data.status = status;
                deferred.resolve(data);
            }).error(function(data, status) {
                data.status = status;
                deferred.reject(data);
            });
            return deferred.promise;
        },
        signOut: function() {
            var deferred = $q.defer();
            $http.get('/api/auth/signOut').success(function(data, status) {
                data.status = status;
                deferred.resolve(data);
            }).error(function(data) {
                data.status = status;
                deferred.reject(data);
            });
            return deferred.promise;
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