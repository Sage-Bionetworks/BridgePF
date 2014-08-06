bridgeShared.service('modalService', ['$log', '$modal', function($log, $modal) {

    return {
        openModal: function(controller, size, url) {
            if (typeof size !== 'string' || typeof url !== 'string') {
                $log.error('Type of size and url for modalService.openModal(controller, size, url) must be string.');
            }
            if (typeof controller !== 'string' && typeof controller !== 'function') {
                $log.error('Type of controller for modalService.openModal(controller, size, url) must be either function or string.');
            }
            
            return $modal.open({
                controller: controller,
                templateUrl: url,
                size: size,
                windowClass: size
            });
        }
    };
}]);