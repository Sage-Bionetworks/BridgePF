bridge.service('formService', [function() {
    return {
        retrieveSpToken: function($route) {
            // route.params don't work here given the way stormpath structures the URL
            var sptoken = $route.current.params.sptoken;
            if (!sptoken) {
                sptoken = (document.location.search+"").split("sptoken=")[1];    
            }
            return sptoken;
        },
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
            if (!formName) {
                throw new Error("You must supply formName to formService.initScope()");
            }
            
            scope.sending = 0;
            scope.$on('loadStart', function() {
                scope.sending++;
            });
            scope.$on('loadEnd', function() {
                scope.sending--;
            });
            
            scope.message = "";
            scope.messageType = "info";
            
            scope.setMessage = function(message, type) {
                scope.messageType = type || "info";
                scope.message = message;
            };
            scope.hasErrors = function(model) {
                return {'has-error': model.$dirty && model.$invalid};
            };
            scope.hasFieldError = function(model, type) {
                return model.$dirty && model.$error[type];
            };
            scope.canSubmit = function() {
                return scope.sending <= 0 && scope[formName].$dirty && scope[formName].$valid;
            };
        }
    };
}]);