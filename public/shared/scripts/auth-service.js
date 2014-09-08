bridgeShared.service('authService', ['$http', '$window', '$q', '$location', '$humane',        
function($http, $window, $q, $location, $humane) {
    var service = {
        sessionToken: '',
        username: '',
        authenticated: false,
        consented: false,
        dataSharing: false,
        
        initSession: function(session) {
            $http.defaults.headers.common['Bridge-Session'] = session.sessionToken;
            
            console.log(session);
            this.sessionToken = session.sessionToken;
            this.username = session.username;
            this.consented = session.consented;
            this.authenticated = session.authenticated;
            this.dataSharing = session.dataSharing;
        },
        clear: function() {
            delete $http.defaults.headers.common['Bridge-Session'];
            this.sessionToken = '';
            this.username = '';
            this.authenticated = false;
            this.consented = false;
            this.dataSharing = false;
        },
        signIn: function(credentials) {
            var deferred = $q.defer();
            $http.post('/api/v1/auth/signIn', credentials).then(function(response) {
                service.initSession(response.data);
                deferred.resolve(response);
            }, function(response) {
                deferred.reject(response);
            });
            return deferred.promise;
        },
        signUp: function(credentials) {
            return $http.post('/api/v1/auth/signUp', credentials);
        },
        signOut: function() {
            return $http.get('/api/v1/auth/signOut');
        },
        resetPassword: function(password, sptoken) {
            return $http.post('/api/v1/auth/resetPassword', {password: password, sptoken: sptoken});
        },
        verifyEmail: function(payload) {
            return $http.post('/api/v1/auth/verifyEmail', payload);
        }
    };

    service.initSession($window._session);
    delete $window._session;
    
    return service;
    
}]);