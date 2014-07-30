describe('ModalService', function() {

    var $log, modalService;

    beforeEach(module('bridge'));

    beforeEach(inject(function($injector) {
        $log = $injector.get('$log');
        modalService = $injector.get('modalService');
    }));

    it('should log error message if url for modal template is not a string.', function() {
        var url = 4;
        var controller = 'SomeController';
        modalService.openModal(url);

        expect($log.error.logs).toContain(['Type of arguments for modalService.openModal(url, controller) must be strings.']);
    });

    it('should log error message if controller name is not a string.', function() {
        var url = '/path/to/temp.html';
        var controller = 4;
        modalService.openModal(url, controller);

        expect($log.error.logs).toContain(['Type of arguments for modalService.openModal(url, controller) must be strings.']);
    });

    it('should create a modal instance.', function() {
        var url = 'path/to/temp.html';
        var controller = 'SomeController';
        var instance = modalService.openModal(url, controller);

        expect(instance).toBeDefined();
    })

});