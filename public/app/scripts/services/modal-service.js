bridge.service('modalService', ['$log', '$http', '$modal', function($log, $http, $modal) {
    
    var profile = null;
    var modal = null;

    var ModalController = ['$scope', 'requestResetPasswordService', function($scope, requestResetPasswordService) {
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

        $scope.submissionFailure = false;
        $scope.submissionSuccess = false;

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
                // Only two items possible to update currently are first name and last name.
                var update = {
                    firstName: $scope.formData.firstName,
                    lastName: $scope.formData.lastName
                };
                $http.post('/api/users/profile', update)
                    .success(function(data, status, headers, config) {
                        $scope.submissionSuccess = true;
                        $log.info(data);
                    })
                    .error(function(data, status, headers, config) {
                        $scope.submissionFailure = true;
                        $log.info(data);
                    });
            }
        };

        $scope.changePassword = function() {
            requestResetPasswordService.open();
        };
    }];
            
    return {
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
}]);