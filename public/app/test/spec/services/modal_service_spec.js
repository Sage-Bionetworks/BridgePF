describe('ModalService', function() {

    var $log, modalService;

    beforeEach(module('bridge'));

    beforeEach(inject(function($injector) {
        $log = $injector.get('$log');
        modalService = $injector.get('modalService');
    }));

    it('should log error message if url for modal template is not a string.', function() {
        var controller = 'SomeController';
        var size = 'sm';
        var url = 4;
        modalService.openModal(controller, size, url);

        expect($log.error.logs).toContain(['Type of size and url for modalService.openModal(controller, size, url) must be string.']);
    });

    it('should log error message if controller name is not a string.', function() {
        var controller = 4;
        var size = 'lg';
        var url = '/path/to/temp.html';
        modalService.openModal(controller, size, url);

        expect($log.error.logs).toContain(['Type of controller for modalService.openModal(controller, size, url) must be either function or string.']);
    });

    it('should create a modal instance.', function() {
        var controller = 'SomeController';
        var size = 'sm';
        var url = 'path/to/temp.html';
        var instance = modalService.openModal(controller, size, url);

        expect(instance).toBeDefined();
    });

});