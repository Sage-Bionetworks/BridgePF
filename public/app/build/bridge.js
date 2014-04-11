angular.module('bridge', []);

angular.module('bridge').controller('MainController', ['$scope','$http', function($scope, $http) {

	$scope.credentials = {};
	
	function handle(data, status) {
		console.log(arguments);
		switch(status) {
		case 401:
			$scope.message = "You must sign in to continue."; break;
		case 412:
			$scope.message = "You must sign the terms of use before continuing."; break;
		default:
			$scope.message = data.payload; break;
		}
	}

	$scope.signIn = function() {
		var p = $http.post('/api/auth/signIn', $scope.credentials)
			.success(function(data, status) {
				$http.defaults.headers.common['Bridge-Session'] = data.payload.sessionToken;
				$scope.message = data.payload.sessionToken;
			}).error(handle);
	};
	$scope.signOut = function() {
		var p = $http.get('/api/auth/signOut')
			.success(function(data, status) {
				delete $http.defaults.headers.common['Bridge-Session'];
				handle(data, status);
			}).error(handle);
	};
	$scope.resetPassword = function() {
		var p = $http.post('/api/auth/resetPassword', {'email': 'test2@sagebase.org'})
			.success(handle).error(handle);
	};
	$scope.getUserProfile = function() {
		var p = $http.get('/api/auth/getUserProfile')
			.success(function(data) {
				$scope.message = data.payload.emails[0];
			}).error(handle);
	};
	$scope.bootstrap = function() {
		var p = $http.get('/bootstrap').success(handle).error(handle);
	};
	
	// They say this exists, but it doesn't.
	console.log($scope);
}]);

var _gaq=[['_setAccount','UA-XXXXXX-X'],['_trackPageview']];

(function() {
    var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
    ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
})();
