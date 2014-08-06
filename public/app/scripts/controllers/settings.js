bridge.controller('SettingsModalController', ['$http', '$humane', '$log', '$modalInstance', '$scope', 'formService', 'modalService', 
    function($http, $humane, $log, $modalInstance, $scope, formService, modalService) {

        formService.initScope($scope, 'settings');

        $http.get('/api/users/profile')
            .success(function(data, status, headers, config) {
                var payload = data.payload;

                // These are all the fields the /api/users/profile call will need to return eventually.
                $scope.profile = {
                    image: "",
                    firstName: payload.firstName,
                    lastName: payload.lastName,
                    username: payload.username,
                    email: payload.email,
                    birthdate: "",
                    questions: "",
                    futureStudies: ""
                };
            })
            .error(function(data, status, headers, config) {
                $humane.error('Sorry, something went wrong while fetching your info. Please close this modal and try again!');
                $log.error('API fetch for users/profile failed.');
            });

        $scope.cancel = function() {
            $modalInstance.dismiss('cancel');
        };

        $scope.submit = function() {
            // Only two items possible to update are first name and last name.
            var update = formService.formToJSON($scope.settings, ['firstName', 'lastName']);
            $http.post('/api/users/profile', update)
                .success(function(data, status, headers, config) {
                    $modalInstance.close('success');
                    $humane.confirm('Your information has been successfully updated.');
                    $log.info(data);
                })
                .error(function(data, status, headers, config) {
                    $scope.setMessage('Woops! Something went wrong on the internet. Please submit again.', 'danger');
                    $log.info(data);
                });
        };
        
        $scope.changePassword = function() {
            modalService.openModal('RequestResetPasswordModalController', 'sm', '/shared/views/requestResetPassword.html');
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