'use strict';

/**
 * Settings user page controller.
 */
angular.module('docs').controller('SettingsUser', function($scope, $state, Restangular, $dialog, $translate) {
  $scope.showRegistrationRequests = false;
  $scope.dateFormat = 'yyyy-MM-dd HH:mm:ss';

  /**
   * Load users from server.
   */
  $scope.loadUsers = function() {
    Restangular.one('user/list').get({
      sort_column: 1,
      asc: true
    }).then(function(data) {
      $scope.users = data.users;
    });
  };

  /**
   * Load registration requests from server.
   */
  $scope.loadRegistrationRequests = function() {
    Restangular.one('registration').get().then(function(data) {
      console.log('registrationRequests lenth:', data.requests.length);
      $scope.registrationRequests = data.requests;
    });
  };

  /**
   * Edit a user.
   */
  $scope.editUser = function(user) {
    $state.go('settings.user.edit', { username: user.username });
  };

  /**
   * Approve a registration request.
   */
  $scope.approveRequest = function(request) {
    var title = $translate.instant('settings.user.registration_request.approve_title');
    var msg = $translate.instant('settings.user.registration_request.approve_message', { username: request.username });
    var btns = [
      { result:'cancel', label: $translate.instant('cancel') },
      { result:'ok', label: $translate.instant('ok'), cssClass: 'btn-primary' }
    ];

    $dialog.messageBox(title, msg, btns, function(result) {
      if (result === 'ok') {
        Restangular.one('registration', request.id).post('approve').then(function() {
          $scope.loadRegistrationRequests();
        });
      }
    });
  };

  /**
   * Reject a registration request.
   */
  $scope.rejectRequest = function(request) {
    var title = $translate.instant('settings.user.registration_request.reject_title');
    var msg = $translate.instant('settings.user.registration_request.reject_message', { username: request.username });
    var btns = [
      { result:'cancel', label: $translate.instant('cancel') },
      { result:'ok', label: $translate.instant('ok'), cssClass: 'btn-primary' }
    ];

    $dialog.messageBox(title, msg, btns, function(result) {
      if (result === 'ok') {
        Restangular.one('registration', request.id).post('reject',{reason:"Manager reject"}).then(function() {
          $scope.loadRegistrationRequests();
        });
      }
    });
  };

  $scope.loadUsers();
  $scope.loadRegistrationRequests();
});