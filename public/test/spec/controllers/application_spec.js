describe("ApplicationController", function() {

    var controller, $scope, $location;

    beforeEach(function() {
        module('bridge');    
        $location = { path: jasmine.createSpy() };
        module(function($provide) {
            $provide.value('$location', $location);
        });
    });
    
    beforeEach(inject(function($controller, $rootScope) {
        $scope = $rootScope.$new();
        controller = $controller('ApplicationController', {'$scope' : $scope });
    }));

    it('A variety of URL defaults are correctly marked as the default page', function() {
        var tab = $scope.tabs[3]; // the last, default tab
        
        $location.path = function() { return "/"; }
        expect($scope.tabClass(tab)).toEqual(true);
        
        $location.path = function() { return "/index.html"; }
        expect($scope.tabClass(tab)).toEqual(true);
        
        $location.path = function() { return ""; }
        expect($scope.tabClass(tab)).toEqual(true);

        $location.path = function() { return "/#/journal"; }
        expect($scope.tabClass(tab)).toEqual(false);
    });
    it("will not allow anonymous to access an authenticated page", function() {
        var event = {preventDefault: angular.identity};
        var next = current = {access: {allowAnonymous: false}};
        
        $scope.$parent.$broadcast("$routeChangeStart", next, {});
        
        expect($location.path).toHaveBeenCalledWith("/");
    });
    it("will allow access to authenticated page when logged in", function() {
        $scope.session.authenticated = true;
        
        var event = {preventDefault: angular.identity};
        var next = current = {access: {allowAnonymous: false}};
        $scope.$parent.$broadcast("$routeChangeStart", next, {});

        expect($location.path).not.toHaveBeenCalledWith("/");
    });
});