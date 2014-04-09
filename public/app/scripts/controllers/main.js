angular.module('bridge').controller('MainController', ['$scope','$http', function($scope, $http) {
	var p = $http({method: 'GET', url: '/api/test'})
		.success(function(data) {
			$scope.message = data.name;
		})
		.error(function() {
			console.error("There has been an error");
		});
}]);
