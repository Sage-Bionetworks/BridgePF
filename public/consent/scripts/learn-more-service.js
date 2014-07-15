angular.module('consent').service('learnMoreService', ['$modal', function($modal) {

    var modalInstance = null;

    var ModalInstanceController = ['$scope', function($scope) {
        $scope.cancel = function () {
            if (modalInstance !== null) {
                modalInstance.dismiss('cancel');
                modalInstance = null;
            }
        };
    }];

    var learnMoreService = {
        open: function(tag) {
            var size = (window.screen.width < 1024) ? 'sm' : 'lg';
            // This will eventually change by study
            var url = "/neurod/views/learn_more/" + tag + ".html";
            modalInstance = $modal.open({
                templateUrl: url,
                controller: ModalInstanceController,
                size: size,
                windowClass: size
            });
        }
    };
    
    return learnMoreService;
}]);