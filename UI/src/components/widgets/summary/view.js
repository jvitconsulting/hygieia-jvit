(function () {
    'use strict';

    angular
        .module(HygieiaConfig.module)
        .controller('SummaryViewController', SummaryViewController);

    SummaryViewController.$inject = ['$scope', 'codeAnalysisData', 'codeRepoData','buildData','deployData','$q', '$uibModal'];
    function SummaryViewController($scope, codeAnalysisData, codeRepoData, buildData, deployData, $q, $uibModal) {

        var ctrl = this;
        ctrl.load = function () {
            var caRequest = {
                componentId: $scope.widgetConfig.componentId,
                max: 20
            };
            var params = {
                componentId: $scope.widgetConfig.componentId,
                numberOfDays: 15
            };
            return $q.all([
                codeAnalysisData.staticDetails(caRequest).then(processCodeAnalysisResponse),
                codeRepoData.resdetails(params).then(processCommitResponse),
                buildData.resdetails(params).then(processBuildResponse),
                deployData.detailsAllComp().then(processDeployResponse)
            ]);

        };


        function processDeployResponse(response) {
            var deferred = $q.defer();
            var i=0;
            // var totalBuildCount = 0;         

            // for(i=0;i<response.result.length;i++) { 
            //     var comp = response.result[i];
            //     totalBuildCount = totalBuildCount + comp.buildResArray.length;
            // }
            ctrl.allCompEnv = response.result;
            // ctrl.totalBuildCount = totalBuildCount;
            
            return deferred.promise;
        }

        function processBuildResponse(response) {
            var deferred = $q.defer();
            var i=0;
            var totalBuildCount = 0;         

            for(i=0;i<response.result.length;i++) { 
                var comp = response.result[i];
                totalBuildCount = totalBuildCount + comp.buildResArray.length;
            }
            ctrl.allCompBuilds = response.result;
            ctrl.totalBuildCount = totalBuildCount;
            
            return deferred.promise;
        }

        function processCommitResponse(response) {
            var deferred = $q.defer();
            var i=0;
            var totalCommitCount = 0;
            
            var topContributor = " "; 
            var topContribution = 0; 

            for(i=0;i<response.result.length;i++) { 
                var comp = response.result[i];
                totalCommitCount = totalCommitCount + comp.resCommits.length;
                if(comp.topContribution>topContribution){
                    topContribution = comp.topContribution;
                    topContributor = comp.topContributor;
                }
            }
            ctrl.allcommitcomps = response.result;
            ctrl.totalCommitCount = totalCommitCount;
            ctrl.topContributor=topContributor;
            ctrl.topContribution=topContribution;
            
            return deferred.promise;
        }


        function processCodeAnalysisResponse(response) {
            var deferred = $q.defer();
            // var caData = _.isEmpty(response.result) ? {} : response.result[0];
            var i=0;
            
            var totalLineCoverage=0;
            var individualCoverage = [];

            var individualLOC = [];
            var totalLoc = 0;
            var grade = 'A';

            for(i=0;i<response.result.length;i++) { 
                
                var caData = response.result[i];
                var lc = parseFloat(getMetric(caData.metrics, 'line_coverage').value);
                var nloc = parseInt(getMetric(caData.metrics, 'ncloc').value);

                totalLoc = totalLoc + nloc;
                totalLineCoverage = totalLineCoverage + lc;
                
                if( lc < 60){
                    grade='F';
                }else if(lc < 70){
                    grade='D';
                }else if(lc < 80){
                    grade='C';
                }else if(lc < 90){
                    grade='B';
                }

                individualCoverage.push({'name':caData.name,'line_coverage':lc, 'nloc':nloc, 'url':caData.url, 'grade':grade });
            }

            ctrl.totalLoc = totalLoc;
            var avglncov = totalLineCoverage/response.result.length;
            ctrl.avgLineCoverage = avglncov
            if( avglncov < 60){
                grade='F';
            }else if(avglncov < 70){
                grade='D';
            }else if(avglncov < 80){
                grade='C';
            }else if(avglncov < 90){
                grade='B';
            }

            ctrl.avgGrade = grade ;
            ctrl.indCoverage = individualCoverage;
            // deferred.resolve(response.lastUpdated);
            return deferred.promise;
        }

        function getMetric(metrics, metricName, title) {
                title = title || metricName;
                return angular.extend((_.find(metrics, { name: metricName }) || { name: title }), { name: title });
        }

    }
})
();


