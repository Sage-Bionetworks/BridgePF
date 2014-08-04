describe('ModalSettingsController', function() {

    var $controller, $httpBackend, $log, controller, scope, modalInstance, requestResetPasswordService;

    beforeEach(module('bridge'));

    beforeEach(inject(function($injector) {
        $log = $injector.get('$log');
        scope = $injector.get('$rootScope').$new();
        modalInstance = { dismiss: jasmine.createSpy('modalInstance.dismiss') };
        requestResetPasswordService = { open: jasmine.createSpy('requestResetPasswordService.open') };

        $httpBackend = $injector.get('$httpBackend');
        $httpBackend.when('GET', '/api/users/profile').respond({
            payload: {
                firstName: 'first name',
                lastName: 'last name',
                username: 'username',
                email: 'username@email.com',
                stormpathHref: 'https://api.stormpath.com/v1/dfasdlafkj2343234adf'
            }
        });
        $httpBackend.expect('GET', '/api/users/profile');

        $controller = $injector.get('$controller');
        controller = $controller('SettingsModalController', {
            '$log': $log,
            '$scope': scope,
            '$modalInstance': modalInstance,
            'requestResetPasswordService': requestResetPasswordService
        });
    }));

    afterEach(function() {
        $httpBackend.flush();
        $httpBackend.verifyNoOutstandingExpectation();
        $httpBackend.verifyNoOutstandingRequest();
    });

    it('should instantiate the controller and retreive the user profile.', function() {
        expect(controller).toBeDefined();
    });

    it('should dismiss the modalInstance when calling close() in the current scope.', function() {
        scope.cancel();

        expect(modalInstance.dismiss).toHaveBeenCalled();
    });

    it('should open the requestResetPasswordService when calling changePassword() in the current scope.', function() {
        scope.changePassword();

        expect(requestResetPasswordService.open).toHaveBeenCalled();
    });

    it('should see a POST request when we submit the form.', function() {
        scope.formData = { firstName: 'first name', lastName: 'last name' };

        $httpBackend.when('POST', '/api/users/profile').respond({
            payload: 'Profile updated.'
        });
        $httpBackend.expect('POST', '/api/users/profile');

        scope.submit();
    });


});