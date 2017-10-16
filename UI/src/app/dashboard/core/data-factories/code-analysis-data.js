/**
 * Gets code quality related data
 */
(function () {
    'use strict';

    angular
        .module(HygieiaConfig.module + '.core')
        .factory('codeAnalysisData', codeAnalysisData);

    function codeAnalysisData($http) {
        var testStaticDetailRoute = 'test-data/ca_detail.json';
        var testSecDetailRoute = 'test-data/ca-security.json';
        var caStaticDetailsRoute = '/api/quality/static-analysis';
        var caStaticDetailsAllRoute = '/api/quality/static-analysis/all';
        var caSecDetailsRoute = '/api/quality/security-analysis';

        return {
            staticDetails: staticDetails,
            securityDetails: securityDetails,
            staticDetailsForAllComponents: staticDetailsForAllComponents
        };

        // get the latest code quality data for the component
        function staticDetails(params) {
            return $http.get(HygieiaConfig.local ? testStaticDetailRoute : caStaticDetailsRoute, { params: params })
                .then(function (response) { return response.data; });
        }

        // get the latest code quality data for all components
        function staticDetailsForAllComponents(params) {
            return $http.get(caStaticDetailsAllRoute, { params: params })
                .then(function (response) { return response.data; });
        }

        function securityDetails(params) {
            return $http.get(HygieiaConfig.local ? testSecDetailRoute : caSecDetailsRoute, { params: params })
                .then(function (response) { return response.data; });
        }
    }
})();
