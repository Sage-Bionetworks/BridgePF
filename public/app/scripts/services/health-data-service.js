bridge.service('healthDataService', ['$http', '$rootScope', '$q', 'loadingService', function($http, $rootScope, $q, loadingService) {

    var service = {
        getAll: function(trackerId) {
            var url = '/api/healthdata/'+trackerId;
            return loadingService.call($http.get(url));
        },
        getByDateRange: function(trackerId, startDate, endDate) {
            var url = '/api/healthdata/'+trackerId+'/'+startDate+'/'+endDate;
            return loadingService.call($http.get(url));
        },
        get: function(trackerId, recordId) {
            var url = '/api/healthdata/'+trackerId+"/record/"+recordId;
            return loadingService.call($http.get(url));
        },
        create: function(trackerId, object) {
            if (object.recordId) {
                throw new Error("Trying to create a record with a pre-existing recordId");
            }
            var url = '/api/healthdata/'+trackerId;
            return loadingService.call($http.post(url, JSON.stringify([object])));
        },
        update: function(trackerId, object) {
            if (!object.recordId) {
                throw new Error("Trying to update a record with no recordId");
            }
            var url = '/api/healthdata/'+trackerId+'/record/'+object.recordId;
            return loadingService.call($http.post(url, JSON.stringify(object)));
        },
        remove: function(trackerId, recordId) {
            var url = '/api/healthdata/'+trackerId+'/record/'+recordId;
            return loadingService.call($http.delete(url));
        },
        createPayload: function(form, dateFields, fields, toMidnight) {
            toMidnight = (typeof toMidnight === "boolean") ? toMidnight : false;
            var startDate = form[dateFields[0]].$modelValue;
            if (toMidnight) {
                startDate.setHours(0,0,0,0);
                startDate = startDate.getTime();
            }
            var endDate = form[dateFields[0]].$modelValue;
            if (toMidnight) {
                endDate.setHours(0,0,0,0);
                endDate = endDate.getTime();
            }
            var payload = { startDate: startDate, endDate: endDate, data: {} };
            fields.forEach(function(field) {
                payload.data[field] = form[field].$modelValue;
            });
            return payload;
        },
        updateRecord: function(record, form, dateFields, fields) {
            record.startDate = form[dateFields[0]].$modelValue.getTime();
            record.endDate = form[dateFields[1]].$modelValue.getTime();
            fields.forEach(function(field) {
                record.data[field] = form[field].$modelValue;
            });
            delete record.$$hashKey; // oh Angular
            return record;
        }
    };
    return service;
}]);