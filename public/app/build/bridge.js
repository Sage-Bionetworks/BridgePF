angular.module('bridge', []);

angular.module('bridge').controller('MainController', ['$scope','$http', function($scope, $http) {
	var p = $http({method: 'GET', url: '/api/test'})
		.success(function(data) {
			$scope.message = data.name;
		})
		.error(function() {
			console.error("There has been an error");
		});
}]);

var _gaq=[['_setAccount','UA-XXXXXX-X'],['_trackPageview']];

(function() {
    var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
    ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
})();
