angular.module('bridge').controller('MainController', ['$scope','$http', function($scope, $http) {
	
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
		var p = $http.post('/api/auth/signIn', {'username': 'test1', 'password': 'password'})
			.success(handle).error(handle);
	};
	$scope.signOut = function() {
		var p = $http.get('/api/auth/signOut')
			.success(handle).error(handle);
	};
	$scope.resetPassword = function() {
		var p = $http.post('/api/auth/resetPassword', {'email': 'test1@sagebase.org'})
			.success(handle).error(handle);
	};
	$scope.getUserProfile = function() {
		var p = $http.get('/api/auth/getUserProfile')
			.success(function(data) {
				$scope.message = data.emails.join(', ');
			}).error(handle);
	};
	$scope.bootstrap = function() {
		var p = $http.get('/bootstrap').success(handle).error(handle);
	};
}]);
