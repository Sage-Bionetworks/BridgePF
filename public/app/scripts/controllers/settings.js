bridge.controller('SettingsModalController', ['$http', '$log', '$modalInstance', '$scope', 'requestResetPasswordService', 
    function($http, $log, $modalInstance, $scope, requestResetPasswordService) {
        $scope.submissionFailure = false;
        $scope.getProfileFailure = false;
        $scope.submissionSuccess = false;

        $scope.profile = $http.get('/api/users/profile')
            .success(function(data, status, headers, config) {
                var profile = data.payload;

                // These are all the fields the /api/users/profile call will need to return eventually.
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
                return profile;
            })
            .error(function(data, status, headers, config) {
                $log.error('API fetch for users/profile failed.');
                $scope.getProfileFailure = true;
                return null;
            });

        $scope.cancel = function() {
            $modalInstance.dismiss('cancel');
        };

        $scope.submit = function() {
            // Only two items possible to update are first name and last name.
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
        };

        $scope.changePassword = function() {
            requestResetPasswordService.open();
        };

        $scope.withdrawStudy = function() {
            $log.info('Withdraw from Study has been clicked.');

            $http.post('/api/consent/withdraw');
            // Need to do something here to interact with user.
        };

        $scope.downloadConsent = function() {
            $log.info('Download consent has been called.');

            $http.get('/api/consent/sendCopy');
        };

        // Not yet implemented on back end. Will need to return to this later.
        $scope.downloadData = function() {
            $log.info('Download data function has been called.');
        };
    }
]);