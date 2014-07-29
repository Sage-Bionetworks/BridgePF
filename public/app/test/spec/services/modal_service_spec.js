describe('ModalService', function() {

    var modalService, log;

    beforeEach(module('bridge'));

    beforeEach(inject(function($injector) {
        modalService = $injector.get('modalService');
        $log = $injector.get('$log');
        $httpBackend = $injector.get('$httpBackend');

        $httpBackend.when('GET', '/api/users/profile').respond({
            payload: {
                firstName: 'first name',
                lastName: 'last name',
                username: 'fl',
                email: 'fl@email.com',
                stormpathHref: 'http://api.stormpath.com/aadfafdasglsadf345354kf'
            }
        });

        $httpBackend.expectGET('/api/users/profile');
    }));

    afterEach(function() {
        $httpBackend.flush();
        $httpBackend.verifyNoOutstandingExpectation();
        $httpBackend.verifyNoOutstandingRequest();
    })

    it('should log error message if url for modal template is not a string.', function() {
        $httpBackend.when('GET', 4).respond('garbage asdl;fkjsadklfjsaf');
        $httpBackend.expectGET(4);

        var url = 4;
        modalService.openModal(url);
        expect($log.error.logs).toContain(['Type of arg for modalService.openModal(arg) must be string.']);
    });

    it('should retrieve profile data and open modal.', function() {
        var url = 'views/settings.html';
        $httpBackend.when('GET', url).respond('<html><body><p>Hello there!</p></body></html>');
        $httpBackend.expectGET(url);
        modalService.openModal(url);
    });

});