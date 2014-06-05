bridge.service('authService', ['$http', '$rootScope', '$location', '$window', '$humane', '$q', 'loadingService',     
function($http, $rootScope, $location, $window, $humane, $q, loadingService) {
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
            loadingService.call($http.post('/api/auth/signIn', credentials)).then(function(data) {
                service.init(data.payload);
                deferred.resolve(data);
            }, function(data) {
                deferred.reject(data);
            });
            return deferred.promise;
        },
        signUp: function(credentials) {
            return loadingService.call($http.post('/api/auth/signUp', credentials));
        },
        signOut: function() {
            return loadingService.call($http.get('/api/auth/signOut'));
        },
        verifyEmail: function(payload) {
            return loadingService.call($http.post('/api/auth/verifyEmail', payload));
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