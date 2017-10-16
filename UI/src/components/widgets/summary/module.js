(function () {
    'use strict';

    var widget_state,
        config = {
            view: {
                defaults: {
                    title: 'Care Orchestrator Summary' // widget title
                },
                controller: 'SummaryViewController',
                controllerAs: 'caWidget',
                templateUrl: 'components/widgets/summary/view.html'
            },
            config: {
                controller: 'SummaryConfigController',
                controllerAs: 'caWidget',
                templateUrl: 'components/widgets/summary/config.html'
            },
            getState: getState,
            collectors: ['codequality']
        };

    angular
        .module(HygieiaConfig.module)
        .config(register);

    register.$inject = ['widgetManagerProvider', 'WidgetState'];
    function register(widgetManagerProvider, WidgetState) {
        widget_state = WidgetState;
        widgetManagerProvider.register('summary', config);
    }

    function getState(widgetConfig) {
        // make sure config values are set
        return HygieiaConfig.local || (widgetConfig.id) ? widget_state.READY : widget_state.CONFIGURE;
    }
})();
