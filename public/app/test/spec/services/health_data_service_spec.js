describe('HealthDataService', function() {

    var healthDataService, form, scope, currentDate, httpBackend, trackerId, recordId;

    beforeEach(module('bridge'));

    beforeEach(inject(function($injector) {
        healthDataService = $injector.get('healthDataService');
        httpBackend = $injector.get('$httpBackend');
        scope = $injector.get('$rootScope').$new();

        currentDate = new Date();
        form = {
            systolic: { $modelValue: 120 },
            diastolic: { $modelValue: 80 },
            date: { $modelValue: currentDate }
        };

    }));

    it('should instantiate the health data service.', function() {
        expect(healthDataService).toBeDefined();
    });

    it('should create a payload with the correct schema, and the dates should now be longs.', function() {
        var payload = healthDataService.createPayload(form, ['date', 'date'], ['systolic', 'diastolic'], true);

        expect(payload.startDate).toEqual(payload.endDate);
        expect(payload.startDate).toEqual(currentDate.getTime());

        expect(payload.data.systolic).toEqual(form.systolic.$modelValue);
        expect(payload.data.diastolic).toEqual(form.diastolic.$modelValue);
    });

    it('should not modify the passed payload when creating or updating records on the server.', function() {
        var payload = healthDataService.createPayload(form, ['date', 'date'], ['systolic', 'diastolic'], false);
        var startDate = payload.startDate;

        var record = {
            recordId: 'abcd-1234-1a2d3',
            version: 1,
            startDate: startDate,
            endDate: startDate
        }

        var trackerId = '1234';
        httpBackend.when('POST', '/api/v1/healthdata/'+trackerId).respond({});
        httpBackend.when('POST', '/api/v1/healthdata/'+trackerId+'/record/'+record.recordId).respond({});

        httpBackend.expect('POST', '/api/v1/healthdata/'+trackerId);
        healthDataService.create(trackerId, payload);
        expect(startDate).toEqual(payload.startDate);

        httpBackend.expect('POST', '/api/v1/healthdata/'+trackerId+'/record/'+recordId);
        healthDataService.update(trackerId, record);
        expect(startDate).toEqual(record.startDate);
    });

});
