/**
 * Gets build related data
 */
(function () {
    'use strict';

    angular
        .module(HygieiaConfig.module + '.core')
        .factory('buildData', buildData);

    function buildData($http) {
        var testDetailRoute = 'test-data/build_detail.json';
        var buildDetailRoute = '/api/build/';
        var allCompBuildDetailRoute = '/api/resbuild/';


        return {
            details: details,
            resdetails: resdetails
        };

        // search for current builds
        function details(params) {
            return $http.get(HygieiaConfig.local ? testDetailRoute : buildDetailRoute, { params: params })
                .then(function (response) {
                    return response.data;
            });
        }


        // search all components for current builds
        function resdetails(params) {
            return $http.get(HygieiaConfig.local ? testDetailRoute : allCompBuildDetailRoute, { params: params })
                .then(function (response) {
                    return response.data;
            });
        }        
    }
})();