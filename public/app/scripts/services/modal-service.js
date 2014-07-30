bridge.service('modalService', ['$log', '$modal', function($log, $modal) {

    return {
        openModal: function(url, controller) {
            if (typeof url !== 'string' || typeof controller !== 'string') {
                $log.error('Type of arguments for modalService.openModal(url, controller) must be strings.');
            }
            
            var size = (window.screen.width < 1024) ? 'sm' : 'lg';
            return $modal.open({
                controller: controller,
                templateUrl: url,
                size: size
            });
        }
    };
}]);