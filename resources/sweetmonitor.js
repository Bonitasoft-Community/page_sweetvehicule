'use strict';
/**
 *
 */

(function() {


var appCommand = angular.module('sweetmonitor', ['googlechart', 'ui.bootstrap','ngSanitize', 'ngModal', 'ngMaterial']);


/* Material : for the autocomplete
 * need
  <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.5.5/angular.min.js"></script>
  <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.5.5/angular-animate.min.js"></script>
  <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.5.5/angular-aria.min.js"></script>
  <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.5.5/angular-messages.min.js"></script>

  <!-- Angular Material Library -->
  <script src="https://ajax.googleapis.com/ajax/libs/angular_material/1.1.0/angular-material.min.js">
 */



// --------------------------------------------------------------------------
//
// Controler Ping
//
// --------------------------------------------------------------------------

// Ping the server
appCommand.controller('SweetVehiculeControler',
	function ( $http, $scope,$sce,$filter ) {

	this.listdelegations=[
		{ 'title' : 'Jan Ficher'},
		{ 'title' : 'William Jobs'}
	];
	
	this.managedelegation=true;
	// -----------------------------------------------------------------------------------------
	//  										MyTask
	// -----------------------------------------------------------------------------------------

	this.myTasks = function(searchText) {
		var self=this;
		console.log("QueryUser HTTP CALL["+searchText+"]");
		this.cleanevent();
		
		self.inprogress=true;
		self.autocomplete.search = searchText;

		var param={ 'delegations' :  self.listdelegations};
		var json = encodeURI( angular.toJson( param, false));
		
		var d = new Date();
		
		return $http.get( '?page=custompage_sweetvehicule&action=mytasks&jsonparam='+json+'&t='+d.getTime() )
		.then( function ( jsonResult ) {
				self.autocomplete.inprogress=false;
				console.log("QueryUser HTTP SUCCESS length="+self.autocomplete.listUsers.length);
				return self.autocomplete.listUsers;
				},  function ( jsonResult ) {
				console.log("QueryUser HTTP THEN");
		});

	  };

	


	<!-- Manage the event -->
	this.getListEvents = function ( listevents ) {
		return $sce.trustAsHtml(  listevents );
	}
	
	this.cleanevent=function()
	{
		this.listevents='';
		this.listeventsrule='';
		this.listeventsruledelete='';
		
	}
	// -----------------------------------------------------------------------------------------
	//  										Manage Delegate
	// -----------------------------------------------------------------------------------------
	this.refreshDelegation = function() {
		var self=this;
		self.inprogress=true;
		this.cleanevent();
		
		var json = encodeURI( angular.toJson( this.display, false));
		var d = new Date();
	
		$http.get( '?page=custompage_sweetvehicule&action=refreshdelegation&paramjson='+json+'&t='+d.getTime())
				.success( function ( jsonResult ) {
						console.log("history",jsonResult);
						self.listevents		= jsonResult.listevents;
						self.listDelegations = jsonResult.listDelegations;
						self.listAffectations = jsonResult.listAffectations;
						self.isAdministrator =  jsonResult.isAdministrator;
						
						self.managedelegation=true;

						self.inprogress=false;


				})
				.error( function() {
					self.inprogress=false;
					});
	}
	
	this.rule= {'affectation': []};
	
	this.addDelegate = function( rule )
	{
		if (rule.delegates === undefined)
			rule.delegates=[];
		
		rule.delegates.push( {} );
	}
	this.removeDelegate = function( index )
	{
		alert("remove item "+angular.toJson(index));
		this.rule.delegates.splice(index, 1);
	}
	
	// -----------------------------------------------------------------------------------------
	//  										Manage Rule
	// -----------------------------------------------------------------------------------------
	this.saveRule = function( rule ) {
		var self=this;
		self.ruleInProgress = rule;
		self.inprogress=true;
		this.cleanevent();
		
		var param={ 'delegationRule' :  rule};
		var json = encodeURI( angular.toJson( param, false));
		var d = new Date();
		
		
		return $http.get( '?page=custompage_sweetvehicule&action=saverule&paramjson='+json+'&t='+d.getTime() )
		.success( function ( jsonResult ) {
				self.inprogress=false;
				self.listeventsrule = jsonResult.listevents;
				
				self.rule.ruleId = jsonResult.delegation.ruleId;
				// in case of create a self rule, fullfull the delegator
				self.rule.delegatorCompleteName = jsonResult.delegation.delegatorCompleteName;
				},  function ( jsonResult ) {
				console.log("QueryUser HTTP THEN");
		});
	
	}
	
	this.addRule = function ()
	{
		this.rule={};
	}
	
	
	this.removeRule = function( rule ) {
		if (! confirm("Would you want to delete the rule "+rule.ruleName+" ?"))
			return;
			
		var self=this;
		self.ruleInProgress = rule;
		self.inprogress=true;
		this.cleanevent();
		
		var param={ 'rule' :  rule, 'ruleId': rule.ruleId};
		var json = encodeURI( angular.toJson( param, false));
		var d = new Date();
		
		
		return $http.get( '?page=custompage_sweetvehicule&action=removerule&paramjson='+json+'&t='+d.getTime() )
		.success( function ( jsonResult ) {
				self.inprogress=false;
				self.listeventsruledelete = jsonResult.listevents;
				alert("list event="+jsonResult.listeventsruledelete);
				self.listDelegations = jsonResult.listDelegations;
				
				self.rule.ruleId = jsonResult.delegation.ruleId;
				},  function ( jsonResult ) {
				console.log("QueryUser HTTP THEN");
		});
	
	}
	this.editRule = function( ruleToEdit )
	{
		select.listeventsruledelete='';
		self.listeventsrule='';
		
		// copy it please to not modify the list
		this.rule=JSON.parse(JSON.stringify(ruleToEdit));
		// transform the string to a date
		this.rule.periodFrom = new Date(this.rule.periodFrom);
		this.rule.periodTo = new Date(this.rule.periodTo);
	}
	// -----------------------------------------------------------------------------------------
	//  										Autocomplete
	// -----------------------------------------------------------------------------------------
	this.autocomplete={};
	
	this.queryUser = function(searchText) {
		var self=this;
		console.log("QueryUser HTTP CALL["+searchText+"]");
		
		self.autocomplete.inprogress=true;
		// self.autocomplete.search = searchText;
		self.inprogress=true;
		
		var param={ 'userfilter' :  searchText };
		
		var json = encodeURI( angular.toJson( param, false));
		// 7.6 : the server force a cache on all URL, so to bypass the cache, then create a different URL
		var d = new Date();
		
		return $http.get( '?page=custompage_sweetvehicule&action=queryusers&paramjson='+json+'&t='+d.getTime() )
		.then( function ( jsonResult ) {
				self.autocomplete.inprogress=false;
			 	self.autocomplete.listUsers =  jsonResult.data.listUsers;
				
				self.inprogress=false;
		
				return self.autocomplete.listUsers;
				},  function ( jsonResult ) {
				console.log("QueryUser HTTP THEN");
		});

	  };

	// -----------------------------------------------------------------------------------------
	//  										Init
	// -----------------------------------------------------------------------------------------
	this.display={ 'showAllRules': true, 'showMyDelegation': true, 'showMyAffectation': true}
	this.init = function()
	{

		var self=this;
		self.inprogress=true;
		this.cleanevent();
		
		var json = encodeURI( angular.toJson( this.display, false));
		var d = new Date();
	
		$http.get( '?page=custompage_sweetvehicule&action=init&paramjson='+json+'&t='+d.getTime())
				.success( function ( jsonResult ) {
						console.log("history",jsonResult);
						self.listevents		= jsonResult.listevents;
						self.listDelegations = jsonResult.listDelegations;
						self.listAffectations = jsonResult.listAffectations;
						self.isAdministrator =  jsonResult.isAdministrator;
						
						self.managedelegation=true;

						self.inprogress=false;


				})
				.error( function() {
					self.inprogress=false;
					});

	}
	this.init();

});



})();