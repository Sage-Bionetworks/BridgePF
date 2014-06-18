describe('bgCompare directive', function() {
    
    var $scope, element, controller;
    
    beforeEach(module('bridge'));
    
    beforeEach(inject(function($compile, $rootScope) {
        $scope = $rootScope;
        element = angular.element('<form name="testForm" bg-compare="testValue,testValueConfirm"><input name="testValue" ng-model="model.testValue"/><input name="testValueConfirm" ng-model="model.testValueConfirm"/></form>');
        $compile(element)($scope);
        $scope.model = {};
        controller = $scope.testForm;
        $scope.$digest();
    }));
    
    xit('should be valid initially', function() {
        expect(controller.$valid).toBe(true);
    });
    
    xit('should be valid after first field has value', function() {
        $scope.model.testValue = "foo";
        $scope.$digest();
        expect(controller.$valid).toBe(true);
    });
    // Cannot figure out how to test this. Ergh.
    xit('should not be valid after second field has different value', function() {
        controller.testValue.$setViewValue('foo');
        controller.testValueConfirm.$setViewValue('bar');
        $scope.$watch();
        console.log(controller);
        expect(controller.$valid).toBe(false);
    });
    
});