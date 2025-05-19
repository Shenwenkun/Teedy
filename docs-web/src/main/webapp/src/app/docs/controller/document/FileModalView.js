'use strict';

/**
 * File modal view controller.
 */
angular.module('docs').controller('FileModalView', function ($uibModalInstance, $scope, $state, $stateParams, $sce, Restangular, $transitions, $http) {
  var setFile = function (files) {
    // Search current file
    _.each(files, function (value) {
      if (value.id === $stateParams.fileId) {
        $scope.file = value;
        $scope.trustedFileUrl = $sce.trustAsResourceUrl('../api/file/' + $stateParams.fileId + '/data');
      }
    });
  };

  // Load files
  Restangular.one('file/list').get({ id: $stateParams.id }).then(function (data) {
    $scope.files = data.files;
    setFile(data.files);

    // File not found, maybe it's a version
    if (!$scope.file) {
      Restangular.one('file/' + $stateParams.fileId + '/versions').get().then(function (data) {
        setFile(data.files);
      });
    }
  });

  /**
   * Return the next file.
   */
  $scope.nextFile = function () {
    var next = undefined;
    _.each($scope.files, function (value, key) {
      if (value.id === $stateParams.fileId) {
        next = $scope.files[key + 1];
      }
    });
    return next;
  };

  /**
   * Return the previous file.
   */
  $scope.previousFile = function () {
    var previous = undefined;
    _.each($scope.files, function (value, key) {
      if (value.id === $stateParams.fileId) {
        previous = $scope.files[key - 1];
      }
    });
    return previous;
  };

  /**
   * Navigate to the next file.
   */
  $scope.goNextFile = function () {
    var next = $scope.nextFile();
    if (next) {
      $state.go('^.file', { id: $stateParams.id, fileId: next.id });
    }
  };

  /**
   * Navigate to the previous file.
   */
  $scope.goPreviousFile = function () {
    var previous = $scope.previousFile();
    if (previous) {
      $state.go('^.file', { id: $stateParams.id, fileId: previous.id });
    }
  };

  /**
   * Open the file in a new window.
   */
  $scope.openFile = function () {
    window.open('../api/file/' + $stateParams.fileId + '/data');
  };

  /**
   * Open the file content a new window.
   */
  $scope.openFileContent = function () {
    window.open('../api/file/' + $stateParams.fileId + '/data?size=content');
  };

  /**
   * Print the file.
   */
  $scope.printFile = function () {
    var popup = window.open('../api/file/' + $stateParams.fileId + '/data', '_blank');
    popup.onload = function () {
      popup.print();
      popup.close();
    }
  };

  /**
   * Close the file preview.
   */
  $scope.closeFile = function () {
    $uibModalInstance.dismiss();
  };

  // Close the modal when the user exits this state
  var off = $transitions.onStart({}, function(transition) {
    if (!$uibModalInstance.closed) {
      if (transition.to().name === $state.current.name) {
        $uibModalInstance.close();
      } else {
        $uibModalInstance.dismiss();
      }
    }
    off();
  });

  /**
   * Return true if we can display the preview image.
   */
  $scope.canDisplayPreview = function () {
    return $scope.file && $scope.file.mimetype !== 'application/pdf';
  };

  /**
   * 判断是否为图片文件
   */
  $scope.isImage = function() {
    return $scope.file && $scope.file.mimetype && $scope.file.mimetype.indexOf('image/') === 0;
  };

  // 添加旋转状态和裁剪器
  $scope.rotation = 0;
  $scope.cropper = null;
  $scope.isCropping = false;

  /**
   * 开始裁剪
   */
  $scope.startCrop = function() {
    $scope.isCropping = true;
    var img = document.querySelector('.modal-fileview img');
    if (!img) return;

    // 初始化裁剪器
    if ($scope.cropper) {
      $scope.cropper.destroy();
    }
    $scope.cropper = new Cropper(img, {
      aspectRatio: NaN,
      viewMode: 2,
      background: true,
      zoomable: true,
      scalable: true
    });
  };

  /**
   * 取消裁剪
   */
  $scope.cancelCrop = function() {
    if ($scope.cropper) {
      $scope.cropper.destroy();
      $scope.cropper = null;
    }
    $scope.isCropping = false;
  };

  /**
   * 保存裁剪
   */
  $scope.saveCrop = function() {
    if (!$scope.cropper) return;

    // 获取裁剪后的 canvas
    var canvas = $scope.cropper.getCroppedCanvas();
    
    // 转换为 blob
    canvas.toBlob(function(blob) {
      // 创建具有正确文件名和类型的文件对象
      var file = new File([blob], 'cropped_image.jpg', { type: 'image/jpeg' });
      var formData = new FormData();
      formData.append('file', file);

      // 使用 PUT 方法上传裁剪后的图片
      var req = {
        method: 'PUT',
        url: '../api/file/' + $stateParams.fileId + '/data',
        headers: {
          'Content-Type': undefined
        },
        data: formData,
        transformRequest: angular.identity
      };

      $http(req).then(function() {
        // 重新加载图片
        var timestamp = new Date().getTime();
        var img = document.querySelector('.modal-fileview img');
        img.src = '../api/file/' + $stateParams.fileId + '/data?ts=' + timestamp;
        // 清理裁剪器
        $scope.cancelCrop();
      }, function(error) {
        console.error('保存图片失败:', error);
        // TODO: 显示错误提示
      });
    }, 'image/jpeg', 0.95);
  };

  /**
   * 旋转图片
   */
  $scope.rotateImage = function(degrees) {
    if ($scope.cropper) {
      $scope.cropper.rotate(degrees);
    } else {
      $scope.rotation = ($scope.rotation + degrees) % 360;
      var img = document.querySelector('.modal-fileview img');
      if (img) {
        img.style.transform = 'rotate(' + $scope.rotation + 'deg)';
        img.style.transition = 'transform 0.3s ease';
      }
    }
  };

  /**
   * 保存旋转后的图片
   */
  $scope.saveRotation = function() {
    if ($scope.rotation === 0) return;
    
    var img = document.querySelector('.modal-fileview img');
    if (!img) return;

    // 创建 canvas 来处理旋转
    var canvas = document.createElement('canvas');
    var ctx = canvas.getContext('2d');

    // 设置 canvas 大小
    if ($scope.rotation === 90 || $scope.rotation === 270) {
      canvas.width = img.naturalHeight;
      canvas.height = img.naturalWidth;
    } else {
      canvas.width = img.naturalWidth;
      canvas.height = img.naturalHeight;
    }

    // 在 canvas 中心旋转
    ctx.translate(canvas.width/2, canvas.height/2);
    ctx.rotate($scope.rotation * Math.PI/180);
    ctx.drawImage(img, -img.naturalWidth/2, -img.naturalHeight/2);

    // 转换为 blob
    canvas.toBlob(function(blob) {
      // 创建具有正确文件名和类型的文件对象
      var file = new File([blob], 'rotated_image.jpg', { type: 'image/jpeg' });
      var formData = new FormData();
      formData.append('file', file);

      // 使用 PUT 方法上传旋转后的图片
      var req = {
        method: 'PUT',
        url: '../api/file/' + $stateParams.fileId + '/data',
        headers: {
          'Content-Type': undefined
        },
        data: formData,
        transformRequest: angular.identity
      };

      $http(req).then(function() {
        // 重新加载图片
        var timestamp = new Date().getTime();
        img.src = '../api/file/' + $stateParams.fileId + '/data?ts=' + timestamp;
        $scope.rotation = 0;
        img.style.transform = '';
      }, function(error) {
        console.error('保存图片失败:', error);
        // TODO: 显示错误提示
      });
    }, 'image/jpeg', 0.95);
  };
});