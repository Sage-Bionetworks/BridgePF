describe("ApplicationController", function() {

    var controller, $scope, $location, $window;

    beforeEach(function() {
        module('bridge');    
        $location = { path: jasmine.createSpy(), search: function() { return {}; } };
        module(function($provide) {
            $provide.value('$location', $location);
            $provide.value('$humane', {confirm: jasmine.createSpy(), error: jasmine.createSpy(), status: jasmine.createSpy()});
            $provide.value('$window', {_session: {sessionToken: 'foo'}});
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
    it("will allow access to authenticated page when logged in", function() {
        $scope.session.authenticated = true;

        var event = {preventDefault: angular.identity};
        var next = current = {};
        $scope.$parent.$broadcast("$routeChangeStart", next, {});

        expect($location.path).not.toHaveBeenCalledWith("/");
    });
});