bridge.service('healthDataService', ['$http', '$rootScope', '$q', 
    function($http, $rootScope, $q) {

    var utils = {
        /*
         * Takes a long that represents number of millisconds since the epoch and returns a string in ISO 8601 format.
         * param: date
         *      long representing number of milliseconds from epoch.
         * returns:
         *      string of date in ISO 8601 format (e.g. '2013-12-22T11:11:23.999Z').
         */
        convertMillisISO: function(date) {
            if (typeof date != 'number') {
                throw new Error("ConvertMillisISO requires a long as input.");
            }
            return new Date(date).toISOString();
        },
        /*
         * Takes a date string in ISO 8601 format and returns a long that represents number of millisconds since the epoch.
         * param: date
         *      string of date in ISO 8601 format (e.g. '2013-12-22T11:11:23.999Z').
         * returns:
         *      long representing number of milliseconds from epoch.
         */
        convertISOMillis: function(date) {
            if (typeof date !== 'string') {
                throw new Error("convertISOMillis requires a string as input.");
            }
            return new Date(date).getTime();
        },
        /*
         * Takes an object and returns a clone of that object (i.e. a copy of the object that is not referenced by the 
         * original object). Handles the cases of object, array, date, string, boolean, and number.
         * param: object
         *      Javascript object to be copied.
         * returns:
         *      a clone of the argument object.
         */
         clone: function(object) {
            // If object is null, undefined, string, number, or boolean, return them immediately as they're immutable.
            if (object === null || typeof object !==  'object') {
                return object;
            }
            if (object instanceof Date) {
                return new Date(object.getTime());
            }
            if (object instanceof Array) {
                return object.map(this.clone);
            }
            if (object instanceof Object) {
                var copy = {};
                for (var attribute in object) {
                    if (object.hasOwnProperty(attribute)) {
                        copy[attribute] = this.clone(object[attribute]);
                    }
                }
                return copy;
            }

            throw new Error("Type of object not supported.");
         },
         /*
         * Takes an object and converts all of its fields designated by dateFields and converts them according to the 
         * rules specified by fn.
         * param: object
         *      Javascript object to be copied.
         * param: fn
         *      function to be executed on each date field.
         * param: dateFields
         *      all fields on which fn will be executed.
         * returns:
         *      nothing. All transformations are by reference on argument object.
         */
         convertObjDateFields: function(object, fn, dateFields) {
            if (typeof object !== 'object') {
                throw new Error("argument object in convertObjDateFields must be an object.");
            } else if (typeof fn !== 'function') {
                throw new Error("argument fn in convertObjDateFields must be a function.");
            } else if (!(dateFields instanceof Array)) {
                throw new Error("argument dateFields in convertObjDateFields must be an array.");
            }
            dateFields.forEach(function(field) {
                if (object.hasOwnProperty(field) && object[field] !== null) {
                    object[field] = fn(object[field]);
                }
            });
         }
    };

    var service = {
        getAll: function(trackerId) {
            var url = '/api/v1/healthdata/'+trackerId;
            return $http.get(url);
        },
        getByDateRange: function(trackerId, startDate, endDate) {
            var start = utils.convertMillisISO(startDate);
            var end = utils.convertMillisISO(endDate);
            var url = '/api/v1/healthdata/'+trackerId+'?startDate='+start+'&endDate='+end;
            return $http.get(url);
        },
        get: function(trackerId, recordId) {
            var url = '/api/v1/healthdata/'+trackerId+"/record/"+recordId;
            return $http.get(url);
        },
        create: function(trackerId, object) {
            if (object.recordId) {
                throw new Error("Trying to create a record with a pre-existing recordId");
            }
            var copy = utils.clone(object);
            utils.convertObjDateFields(copy, utils.convertMillisISO, ['startDate', 'endDate']);

            var url = '/api/v1/healthdata/'+trackerId;
            return $http.post(url, JSON.stringify([copy]));
        },
        update: function(trackerId, object) {
            if (!object.recordId) {
                throw new Error("Cannot update a record with no recordId");
            } else if (typeof object.version === "undefined") {
                throw new Error("Cannot update a record with no version");
            }
            var copy = utils.clone(object);
            utils.convertObjDateFields(copy, utils.convertMillisISO, ['startDate', 'endDate']);

            var url = '/api/v1/healthdata/'+trackerId+'/record/'+copy.recordId;
            return $http.post(url, JSON.stringify(copy));
        },
        remove: function(trackerId, recordId) {
            var url = '/api/v1/healthdata/'+trackerId+'/record/'+recordId;
            return $http['delete'](url);
        },
        createPayload: function(form, dateFields, fields, toMidnight) {
            toMidnight = (typeof toMidnight === "boolean") ? toMidnight : false;
            var startDate = form[dateFields[0]].$modelValue;
            if (toMidnight) {
                startDate.setHours(0,0,0,0);
            }
            var endDate = form[dateFields[0]].$modelValue;
            if (toMidnight) {
                endDate.setHours(0,0,0,0);
            }
            startDate = startDate.getTime();
            endDate = endDate.getTime();

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
