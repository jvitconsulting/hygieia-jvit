(function () {
    'use strict';

    angular
        .module(HygieiaConfig.module)
        .controller('RepoViewController', RepoViewController);

    RepoViewController.$inject = ['$q', '$scope', 'codeRepoData', 'collectorData', '$uibModal'];
    function RepoViewController($q, $scope, codeRepoData, collectorData, $uibModal) {
        var ctrl = this;

        ctrl.commitChartOptions = {
            plugins: [
                Chartist.plugins.gridBoundaries(),
                Chartist.plugins.lineAboveArea(),
                Chartist.plugins.pointHalo(),
                Chartist.plugins.ctPointClick({
                    onClick: showDetail
                }),
                Chartist.plugins.axisLabels({
                    stretchFactor: 1.4,
                    axisX: {
                        labels: [
                            moment().subtract(14, 'days').format('MMM DD'),
                            moment().subtract(7, 'days').format('MMM DD'),
                            moment().format('MMM DD')
                        ]
                    }
                }),
                Chartist.plugins.ctPointLabels({
                    textAnchor: 'middle'
                })
            ],
            showArea: true,
            lineSmooth: false,
            fullWidth: true,
            axisY: {
                offset: 30,
                showGrid: true,
                showLabel: true,
                labelInterpolationFnc: function (value) {
                    return Math.round(value * 100) / 100;
                }
            }
        };

        ctrl.commits = [];
        ctrl.showDetail = showDetail;
        ctrl.load = function () {
            var deferred = $q.defer();
            var params = {
                componentId: $scope.widgetConfig.componentId,
                numberOfDays: 14
            };

            codeRepoData.details(params).then(function (data) {
                processResponse(data.result, params.numberOfDays);
                ctrl.lastUpdated = data.lastUpdated;
            }).then(function () {
                collectorData.getCollectorItem($scope.widgetConfig.componentId, 'scm').then(function (data) {
                    deferred.resolve( {lastUpdated: ctrl.lastUpdated, collectorItem: data});
                });
            });
            return deferred.promise;
        };

        function showDetail(evt) {
            var target = evt.target,
                pointIndex = target.getAttribute('ct:point-index');

            $uibModal.open({
                controller: 'RepoDetailController',
                controllerAs: 'detail',
                templateUrl: 'components/widgets/repo/detail.html',
                size: 'lg',
                resolve: {
                    commits: function () {
                        return groupedCommitData[pointIndex];
                    }
                }
            });
        }

        var groupedCommitData = [];

        function processResponse(data, numberOfDays) {
            // get total commits by day
            var commits = [];
            var groups = _(data).sortBy('timestamp')
                .groupBy(function (item) {
                    return -1 * Math.floor(moment.duration(moment().diff(moment(item.scmCommitTimestamp))).asDays());
                }).value();

            for (var x = -1 * numberOfDays + 1; x <= 0; x++) {
                if (groups[x]) {
                    commits.push(groups[x].length);
                    groupedCommitData.push(groups[x]);
                }
                else {
                    commits.push(0);
                    groupedCommitData.push([]);
                }
            }

            //update charts
            if (commits.length) {
                var labels = [];
                _(commits).forEach(function (c) {
                    labels.push('');
                });

                ctrl.commitChartData = {
                    series: [commits],
                    labels: labels
                };
            }


            // group get total counts and contributors
            var today = toMidnight(new Date());
            var sevenDays = toMidnight(new Date());
            var fourteenDays = toMidnight(new Date());
            sevenDays.setDate(sevenDays.getDate() - 7);
            fourteenDays.setDate(fourteenDays.getDate() - 14);

            var lastDayCount = 0;
            var lastDayContributors = [];
            var lastDayTopContributorsMap = {};

            var lastSevenDayCount = 0;
            var lastSevenDaysContributors = [];
            var lastSevenDaysTopContributorsMap = {};

            var lastFourteenDayCount = 0;
            var lastFourteenDaysContributors = [];
            var lastFourteenDaysTopContributorsMap = {};

            // loop through and add to counts
            _(data).forEach(function (commit) {

                if (commit.scmCommitTimestamp >= today.getTime()) {
                    lastDayCount++;
                    if(!lastDayTopContributorsMap[commit.scmAuthor]){
                        lastDayTopContributorsMap[commit.scmAuthor] =1;
                    }
                    lastDayTopContributorsMap[commit.scmAuthor] = lastDayTopContributorsMap[commit.scmAuthor]+1;
                    if (lastDayContributors.indexOf(commit.scmAuthor) === -1) {
                        lastDayContributors.push(commit.scmAuthor);
                    }
                }

                if (commit.scmCommitTimestamp >= sevenDays.getTime()) {
                    lastSevenDayCount++;
                    if(!lastSevenDaysTopContributorsMap[commit.scmAuthor]){
                        lastSevenDaysTopContributorsMap[commit.scmAuthor] =1;
                    }
                    lastSevenDaysTopContributorsMap[commit.scmAuthor] = lastSevenDaysTopContributorsMap[commit.scmAuthor]+1;
                    if (lastSevenDaysContributors.indexOf(commit.scmAuthor) === -1) {
                        lastSevenDaysContributors.push(commit.scmAuthor);
                    }
                }

                if (commit.scmCommitTimestamp >= fourteenDays.getTime()) {
                    lastFourteenDayCount++;
                    ctrl.commits.push(commit);
                    if(!lastFourteenDaysTopContributorsMap[commit.scmAuthor]){
                        lastFourteenDaysTopContributorsMap[commit.scmAuthor] =1;
                    }
                    lastFourteenDaysTopContributorsMap[commit.scmAuthor] = lastFourteenDaysTopContributorsMap[commit.scmAuthor]+1;
                    if (lastFourteenDaysContributors.indexOf(commit.scmAuthor) === -1) {
                        lastFourteenDaysContributors.push(commit.scmAuthor);
                    }
                }

            });

            var value = 0 ; 
            var lastDayTopContribution = 0;
            var lastDayTopContributor = '';
            Object.keys(lastDayTopContributorsMap).forEach(function(key) {
                value = lastDayTopContributorsMap[key];
                if( value > lastDayTopContribution){
                    lastDayTopContribution = value ;
                    lastDayTopContributor = key;
                }
            });

            var lastSevenDayTopContribution = 0;
            var lastSevenDayTopContributor = '';
            Object.keys(lastSevenDaysTopContributorsMap).forEach(function(key) {
                value = lastSevenDaysTopContributorsMap[key];
                if( value > lastSevenDayTopContribution){
                    lastSevenDayTopContribution = value ;
                    lastSevenDayTopContributor = key;
                }
            });

            var lastFourteenDayTopContribution = 0;
            var lastFourteenDayTopContributor = '';
            Object.keys(lastFourteenDaysTopContributorsMap).forEach(function(key) {
                value = lastFourteenDaysTopContributorsMap[key];
                if( value > lastFourteenDayTopContribution){
                    lastFourteenDayTopContribution = value ;
                    lastFourteenDayTopContributor = key;
                }
            });

            ctrl.lastDayCommitCount = lastDayCount;
            ctrl.lastDayContributorCount = lastDayContributors.length;
            ctrl.lastSevenDaysCommitCount = lastSevenDayCount;
            ctrl.lastSevenDaysContributorCount = lastSevenDaysContributors.length;
            ctrl.lastFourteenDaysCommitCount = lastFourteenDayCount;
            ctrl.lastFourteenDaysContributorCount = lastFourteenDaysContributors.length;

            ctrl.lastDayTopContributor = lastDayTopContributor;
            ctrl.lastSevenDayTopContributor = lastSevenDayTopContributor;
            ctrl.lastFourteenDayTopContributor = lastFourteenDayTopContributor;


            function toMidnight(date) {
                date.setHours(0, 0, 0, 0);
                return date;
            }
        }

    }
})();
