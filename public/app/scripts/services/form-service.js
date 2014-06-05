bridge.service('formService', [function() {
    return {
        formToJSON: function(form, fields) {
            var object = {};
            for (var i=0; i < fields.length; i++) {
                if (form[fields[i]].$modelValue && form[fields[i]].$valid) {
                    object[fields[i]] = form[fields[i]].$modelValue;
                }
            }
            return object;
        },
        /**
         * These properties and methods are valuable for any Ajax-submitted 
         * form and I was copying them from dialog controller to dialog 
         * controller, so they are collected here as a mix-in.
         */
        initScope: function(scope, formName) {
            scope.sending = false;
            scope.message = "";

            scope.hasErrors = function(model) {
                return {'has-error': model.$dirty && model.$invalid};
            };
            scope.hasFieldError = function(model, type) {
                return model.$dirty && model.$error[type];
            };
            scope.canSubmit = function() {
                return !scope.sending && scope[formName].$dirty && scope[formName].$valid;
            };
        }
    };
}]);