module.service('authService', ['$http', '$rootScope', '$location', '$window', '$humane', '$q',      
function($http, $rootScope, $location, $window, $humane, $q) {
    var service = {
        sessionToken: '',
        username: '',
        authenticated: false,
        consented: false,
        
        handleConsent: function(payload) {
            if (payload) {
                this.init(payload);    
            }
            $location.path("/consent");
        },
        init: function(data) {
            $http.defaults.headers.common['Bridge-Session'] = data.sessionToken;
            this.sessionToken = data.sessionToken;
            this.username = data.username;
            this.consented = data.consented;
            this.authenticated = true;
        },
        clear: function() {
            delete $http.defaults.headers.common['Bridge-Session'];
            this.sessionToken = '';
            this.username = '';
            this.authenticated = false;
            this.consented = false;
        },
        signIn: function(credentials) {
            var deferred = $q.defer();
            $http.post('/api/auth/signIn', credentials).then(function(response) {
                service.init(response.data.payload);
                deferred.resolve(response);
            }, function(response) {
                deferred.reject(response);
            });
            return deferred.promise;
        },
        signUp: function(credentials) {
            return $http.post('/api/auth/signUp', credentials);
        },
        signOut: function() {
            return $http.get('/api/auth/signOut');
        },
        resetPassword: function(password, sptoken) {
            return $http.post('/api/auth/resetPassword', {password: password, sptoken: sptoken});
        },
        verifyEmail: function(payload) {
            return $http.post('/api/auth/verifyEmail', payload);
        }
    };

    angular.extend(service, window.bridgeAuth);
    delete window.bridgeAuth;

    // Anonymous users can't access user routes.
    /* It's all anonymous
    $rootScope.$on('$routeChangeStart', function(e, next, current) {
        if (!next.access.allowAnonymous && !service.authenticated) {
            console.warn("Page requires authentication, redirecting");
            e.preventDefault();
            $location.path("/");
        }
    });
    */
    
    return service;
    
}]);