bridgeAuth.service('authService', ['$http', '$window', '$q', '$location', '$humane',        
function($http, $window, $q, $location, $humane) {
    var service = {
        sessionToken: '',
        username: '',
        authenticated: false,
        consented: false,
        
        initSession: function(session) {
            $http.defaults.headers.common['Bridge-Session'] = session.sessionToken;
            
            this.sessionToken = session.sessionToken;
            this.username = session.username;
            this.consented = session.consented;
            this.authenticated = session.authenticated;
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
                service.initSession(response.data.payload);
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

    service.initSession($window._session);
    delete $window._session;
    
    return service;
    
}]);