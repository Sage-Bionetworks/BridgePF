bridge.service('modalService', ['$log', '$http', '$modal', function($log, $http, $modal) {
    
    var profile = null;
    var modal = null;

    var ModalController = ['$scope', function($scope) {
        $scope.formData = {
            image: "",
            firstName: profile.firstName,
            lastName: profile.lastName,
            username: profile.username,
            email: profile.email,
            birthdate: "",
            questions: "",
            futureStudies: ""
        };

        $scope.profile = profile;

        $scope.cancel = function() {
            // modal must be open to be closed.
            if (modal !== null) {
                modal.dismiss('cancel');
                modal = null;
            }
        };
        $scope.submit = function() {
            // modal must be open to submit.
            if (modal !== null) {
                
            }
        };
    }];
            
    var service = {
        openModal: function(url) {
            if (typeof url !== 'string') {
                $log.error('Type of arg for modalService.openModal(arg) must be string.');
            }
            $http.get('/api/users/profile')
                .success(function(data, status, headers, config) {
                    profile = data.payload;
                    var size = (window.screen.width < 1024) ? 'sm' : 'lg';
                    modal = $modal.open({
                        controller: ModalController,
                        templateUrl: url,
                        size: size
                    });
                })
                .error(function(data, status, headers, config) {
                    $log.error('api fetch for users/profile failed.');
                });
        }
    };

    return service;
}]);