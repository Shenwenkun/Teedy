'use strict';

/**
 * Main controller.
 */
angular.module('docs').controller('Main', function($scope, $rootScope, $state, User) {
  User.userInfo().then(function(data) {
    if (data.anonymous) {
      $state.go('login', {}, {
        location: 'replace',
        notify: false
      });
    } else {
      $state.go('document.default', {}, {
        location: 'replace',
        notify: false
      });
    }
  });
});